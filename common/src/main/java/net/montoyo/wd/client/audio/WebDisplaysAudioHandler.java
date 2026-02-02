package net.montoyo.wd.client.audio;

import net.minecraft.client.Minecraft;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.Log;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefAudioHandlerAdapter;
import org.cef.misc.CefAudioParameters;
import org.cef.misc.CefChannelLayout;
import org.cef.misc.DataPointer;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audio handler that captures audio from CEF browsers and plays it through Minecraft's audio system.
 * This allows volume control and 3D positional audio.
 */
public class WebDisplaysAudioHandler extends CefAudioHandlerAdapter {

    public static WebDisplaysAudioHandler INSTANCE;
    
    // Map of browser ID to audio stream
    private final Map<Integer, BrowserAudioStream> audioStreams = new ConcurrentHashMap<>();

    public WebDisplaysAudioHandler() {
        INSTANCE = this;
    }
    
    @Override
    public boolean getAudioParameters(CefBrowser browser, CefAudioParameters params) {
        // Let CEF handle playback through the OS; volume/distance handled via BrowserVolumeManager.
        Log.info("getAudioParameters called for browser %d - letting CEF play to OS", browser.getIdentifier());
        return false;
    }
    
    @Override
    public void onAudioStreamStarted(CefBrowser browser, CefAudioParameters params, int channels) {
        // Should not be called when getAudioParameters returns false, but guard anyway.
        int actualChannels = channels;
        // Derive channel count from layout when possible
        if (params.channelLayout != null) {
            if (params.channelLayout == CefChannelLayout.CEF_CHANNEL_LAYOUT_MONO) {
                actualChannels = 1;
            } else if (params.channelLayout == CefChannelLayout.CEF_CHANNEL_LAYOUT_STEREO) {
                actualChannels = 2;
            }
        }
        if (actualChannels <= 0) actualChannels = 2;

        int sampleRate = (params.sampleRate > 0) ? params.sampleRate : 48000;

        Log.info("Audio stream started for browser %d: %d Hz, %d channels (layout %s, frames/buffer %d)",
                browser.getIdentifier(), sampleRate, actualChannels, params.channelLayout, params.framesPerBuffer);

        // Create a new audio stream for this browser
        BrowserAudioStream stream = new BrowserAudioStream(browser, sampleRate, actualChannels);
        audioStreams.put(browser.getIdentifier(), stream);
    }
    
    @Override
    public void onAudioStreamPacket(CefBrowser browser, DataPointer data, int frames, long pts) {
        // OS playback path: ignore OpenAL buffering.
    }
    
    @Override
    public void onAudioStreamStopped(CefBrowser browser) {
        Log.info("Audio stream stopped for browser %d", browser.getIdentifier());
        BrowserAudioStream stream = audioStreams.remove(browser.getIdentifier());
        if (stream != null) {
            stream.stop();
        }
    }
    
    @Override
    public void onAudioStreamError(CefBrowser browser, String text) {
        Log.error("Audio stream error for browser %d: %s", browser.getIdentifier(), text);
    }
    
    /**
     * Get the screen data and block entity for a browser.
     */
    private ScreenInfo getScreenForBrowser(CefBrowser browser) {
        // Search through all loaded screens to find the one with this browser
        ClientProxy proxy = (ClientProxy) WebDisplays.PROXY;
        ScreenInfo best = null;
        float bestVolume = -1.0f;
        for (ScreenBlockEntity be : proxy.getScreens()) {
            for (int i = 0; i < be.screenCount(); i++) {
                ScreenData screen = be.getScreen(i);
                if (screen != null && screen.browser == browser) {
                    float volume = calculateVolume(screen, be);
                    if (volume > bestVolume) {
                        bestVolume = volume;
                        best = new ScreenInfo(screen, be);
                    }
                }
            }
        }
        return best;
    }

    private static class ScreenInfo {
        final ScreenData screen;
        final ScreenBlockEntity blockEntity;

        ScreenInfo(ScreenData screen, ScreenBlockEntity blockEntity) {
            this.screen = screen;
        this.blockEntity = blockEntity;
        }
    }

    /**
     * Called every client tick on the main thread to push queued audio to OpenAL.
     */
    public void clientTick() {
        for (BrowserAudioStream stream : audioStreams.values()) {
            stream.drainQueuedAudio();
        }
    }

    /**
     * Represents an audio stream for a single browser.
     */
    private class BrowserAudioStream {
        private final CefBrowser browser;
        private final int sampleRate;
        private final int channels;
        private int alSource = -1;
        private final int[] alBuffers = new int[3]; // Triple buffering
        private int currentBuffer = 0;
        private boolean initialized = false;
        private int packetCount = 0; // For logging
        private final Queue<ByteBuffer> pending = new LinkedList<>();
        private boolean warnedNotInit = false;

        public BrowserAudioStream(CefBrowser browser, int sampleRate, int channels) {
            this.browser = browser;
            this.sampleRate = sampleRate;
            this.channels = channels;

            try {
                // Create OpenAL source
                alSource = AL10.alGenSources();
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    Log.error("Failed to create OpenAL source for browser audio");
                    return;
                }

                // Create OpenAL buffers
                AL10.alGenBuffers(alBuffers);
                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    Log.error("Failed to create OpenAL buffers for browser audio");
                    AL10.alDeleteSources(alSource);
                    alSource = -1;
                    return;
                }

                initialized = true;
            } catch (Exception e) {
                Log.error("Error initializing OpenAL for browser audio: %s", e.getMessage());
            }
        }

        public void addAudioData(DataPointer data, int frames) {
            if (!initialized) {
                if (!warnedNotInit) {
                    Log.warning("addAudioData called but stream not initialized for browser %d", browser.getIdentifier());
                    warnedNotInit = true;
                }
                return;
            }

            packetCount++;

            try {
                // Log every 100 packets to avoid spam
                if (packetCount % 100 == 0 || packetCount <= 3) {
                    Log.info("Received audio packet for browser %d: frames=%d, packet=%d, pending=%d",
                            browser.getIdentifier(), frames, packetCount, pending.size());
                }

                // Convert CEF audio data (planar float32) to interleaved format and queue it.
                ByteBuffer audioBuffer = convertAudioData(data, frames, channels, 1.0f);
                if (audioBuffer == null) {
                    Log.error("Failed to convert audio data for browser %d", browser.getIdentifier());
                    return;
                }

                // Avoid unbounded growth
                if (pending.size() > 20) {
                    pending.poll();
                }
                pending.offer(audioBuffer);
            } catch (Exception e) {
                Log.error("Error processing browser audio: %s", e.getMessage());
                e.printStackTrace();
            }
        }

        public void drainQueuedAudio() {
            if (!initialized) {
                return;
            }

            try {
                ScreenInfo info = getScreenForBrowser(browser);
                if (info == null) {
                    return;
                }

                // Update volume and position each tick
                float volume = calculateVolume(info.screen, info.blockEntity);
                updateSourcePosition(info.blockEntity);

                // Unqueue processed buffers to avoid overflow
                int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
                if (processed > 0) {
                    int[] freedBuffers = new int[processed];
                    AL10.alSourceUnqueueBuffers(alSource, freedBuffers);
                }

                // Queue pending audio
                while (!pending.isEmpty()) {
                    int bufferId = getNextBuffer();
                    if (bufferId == -1) {
                        break;
                    }
                    ByteBuffer audioBuffer = pending.poll();
                    if (audioBuffer == null) break;
                    int format = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                    // Apply volume when uploading to OpenAL
                    applyVolume(audioBuffer, volume);
                    AL10.alBufferData(bufferId, format, audioBuffer, sampleRate);
                    int err = AL10.alGetError();
                    if (err != AL10.AL_NO_ERROR) {
                        Log.error("OpenAL error buffering data for browser %d: %d", browser.getIdentifier(), err);
                    }
                    AL10.alSourceQueueBuffers(alSource, bufferId);
                }

                int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
                if (state != AL10.AL_PLAYING && AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED) > 0) {
                    Log.info("Starting OpenAL playback for browser %d", browser.getIdentifier());
                    AL10.alSourcePlay(alSource);
                }
            } catch (Exception e) {
                Log.error("Error draining audio queue: %s", e.getMessage());
            }
        }

        private int getNextBuffer() {
            // Unqueue processed buffers to free them
            int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
            if (processed > 0) {
                int[] freedBuffers = new int[processed];
                AL10.alSourceUnqueueBuffers(alSource, freedBuffers);
                return freedBuffers[0];
            }

            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued >= alBuffers.length) {
                return -1;
            }

            // Use next buffer in rotation
            int buffer = alBuffers[currentBuffer];
            currentBuffer = (currentBuffer + 1) % alBuffers.length;
            return buffer;
        }

        private void updateSourcePosition(ScreenBlockEntity be) {
            if (be == null) return;
            float x = (float) (be.getBlockPos().getX() + 0.5);
            float y = (float) (be.getBlockPos().getY() + 0.5);
            float z = (float) (be.getBlockPos().getZ() + 0.5);
            AL10.alSource3f(alSource, AL10.AL_POSITION, x, y, z);
        }

        private void applyVolume(ByteBuffer buffer, float volume) {
            buffer.rewind();
            int samples = buffer.remaining() / 2;
            for (int i = 0; i < samples; i++) {
                short s = buffer.getShort(i * 2);
                short scaled = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (s * volume)));
                buffer.putShort(i * 2, scaled);
            }
            buffer.rewind();
        }

        public void stop() {
            if (!initialized) return;

            try {
                if (alSource != -1) {
                    AL10.alSourceStop(alSource);
                    AL10.alDeleteSources(alSource);
                    alSource = -1;
                }
                AL10.alDeleteBuffers(alBuffers);
            } catch (Exception e) {
                Log.error("Error stopping browser audio: %s", e.getMessage());
            }
        }
    }

    private float calculateVolume(ScreenData screen, ScreenBlockEntity be) {
        float baseVolume = screen.volume / 100.0f;

        if (!screen.autoVolume) {
            return baseVolume;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || be == null) {
            return baseVolume;
        }

        double dx = mc.player.getX() - (be.getBlockPos().getX() + 0.5);
        double dy = mc.player.getY() - (be.getBlockPos().getY() + 0.5);
        double dz = mc.player.getZ() - (be.getBlockPos().getZ() + 0.5);
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        float minDist = WebDisplays.INSTANCE.avDist100;
        float maxDist = WebDisplays.INSTANCE.avDist0;

        if (distance <= minDist) {
            return baseVolume;
        } else if (distance >= maxDist) {
            return 0.0f;
        } else {
            float attenuation = 1.0f - ((distance - minDist) / (maxDist - minDist));
            return baseVolume * attenuation;
        }
    }

    /**
     * Convert CEF audio data (planar float32) to interleaved int16 format with volume applied.
     */
    private ByteBuffer convertAudioData(DataPointer data, int frames, int channels, float volume) {
        try {
            // CEF provides audio as planar float32 (separate arrays for each channel)
            // We need to convert to interleaved int16 for OpenAL

            int totalSamples = frames * channels;
            ByteBuffer output = ByteBuffer.allocateDirect(totalSamples * 2); // 2 bytes per sample (int16)
            output.order(ByteOrder.nativeOrder());

            // Get pointers to each channel's data
            DataPointer[] channelPointers = new DataPointer[channels];
            for (int ch = 0; ch < channels; ch++) {
                channelPointers[ch] = data.getData(ch);
            }

            // Interleave and convert float32 to int16
            for (int frame = 0; frame < frames; frame++) {
                for (int ch = 0; ch < channels; ch++) {
                    // DataPointer.getFloat expects byte offset; frame index * 4
                    float sample = channelPointers[ch].getFloat(frame * 4);

                    // Apply volume
                    sample *= volume;

                    // Clamp to [-1.0, 1.0]
                    sample = Math.max(-1.0f, Math.min(1.0f, sample));

                    // Convert to int16
                    short sampleInt16 = (short) (sample * 32767.0f);
                    output.putShort(sampleInt16);
                }
            }

            output.flip();
            return output;
        } catch (Exception e) {
            Log.error("Error converting audio data: %s", e.getMessage());
            return null;
        }
    }
}
