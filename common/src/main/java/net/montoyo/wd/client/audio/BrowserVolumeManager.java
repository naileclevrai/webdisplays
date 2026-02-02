package net.montoyo.wd.client.audio;

import net.minecraft.client.Minecraft;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.Log;
import org.cef.browser.CefBrowser;

import java.util.Locale;

/**
 * Manages browser volume by injecting JavaScript to control HTML5 audio/video elements.
 * This is simpler than capturing audio and allows the OS to handle playback.
 */
public class BrowserVolumeManager {
    
    private static final String VOLUME_CONTROL_SCRIPT =
        "(function() {" +
        "  if (window.webdisplaysVolumeControl) return;" +
        "  window.webdisplaysVolumeControl = true;" +
        "  window.__webdisplaysContexts = window.__webdisplaysContexts || [];" +
        "  window.setWebDisplaysVolume = function(vol) {" +
        "    var elements = document.querySelectorAll('video, audio');" +
        "    for (var i = 0; i < elements.length; i++) {" +
        "      try { elements[i].muted = false; elements[i].volume = vol; } catch (e) {}" +
        "    }" +
        "    if (window.__webdisplaysContexts) {" +
        "      for (var j = 0; j < window.__webdisplaysContexts.length; j++) {" +
        "        var ctx = window.__webdisplaysContexts[j];" +
        "        if (ctx && ctx.__webdisplaysGain) {" +
        "          try { ctx.__webdisplaysGain.gain.value = vol; } catch (e) {}" +
        "        }" +
        "      }" +
        "    }" +
        "  };" +
        "  function getGain(ctx) {" +
        "    if (!ctx) return null;" +
        "    if (!ctx.__webdisplaysGain) {" +
        "      try {" +
        "        var g = ctx.createGain();" +
        "        g.gain.value = (window.currentWebDisplaysVolume !== undefined) ? window.currentWebDisplaysVolume : 1.0;" +
        "        g.connect(ctx.destination);" +
        "        ctx.__webdisplaysGain = g;" +
        "      } catch (e) { return null; }" +
        "    }" +
        "    return ctx.__webdisplaysGain;" +
        "  }" +
        "  function setAllMediaVolumes(vol) {" +
        "    var elements = document.querySelectorAll('video, audio');" +
        "    for (var i = 0; i < elements.length; i++) {" +
        "      try { elements[i].muted = false; elements[i].volume = vol; } catch (e) {}" +
        "    }" +
        "  }" +
        "  function patchAudioContext(AC) {" +
        "    if (!AC || AC.__webdisplaysPatched) return;" +
        "    AC.__webdisplaysPatched = true;" +
        "    var Orig = AC;" +
        "    function wrapCtor() {" +
        "      var ctx = new Orig();" +
        "      try { window.__webdisplaysContexts.push(ctx); } catch (e) {}" +
        "      return ctx;" +
        "    }" +
        "    if (window.AudioContext === AC) {" +
        "      window.AudioContext = wrapCtor;" +
        "      window.AudioContext.prototype = Orig.prototype;" +
        "      window.AudioContext.__proto__ = Orig;" +
        "    } else if (window.webkitAudioContext === AC) {" +
        "      window.webkitAudioContext = wrapCtor;" +
        "      window.webkitAudioContext.prototype = Orig.prototype;" +
        "      window.webkitAudioContext.__proto__ = Orig;" +
        "    }" +
        "    if (window.AudioNode && window.AudioNode.prototype && !window.AudioNode.prototype.__webdisplaysPatched) {" +
        "      window.AudioNode.prototype.__webdisplaysPatched = true;" +
        "      var origConnect = window.AudioNode.prototype.connect;" +
        "      window.AudioNode.prototype.connect = function() {" +
        "        try {" +
        "          var dest = arguments[0];" +
        "          if (dest && this.context && dest === this.context.destination) {" +
        "            var g = getGain(this.context);" +
        "            if (g) return origConnect.call(this, g);" +
        "          }" +
        "        } catch (e) {}" +
        "        return origConnect.apply(this, arguments);" +
        "      };" +
        "    }" +
        "  }" +
        "  function applyControl() {" +
        "    if (!document || !document.body) return false;" +
        "    if (window.currentWebDisplaysVolume !== undefined) {" +
        "      window.setWebDisplaysVolume(window.currentWebDisplaysVolume);" +
        "    }" +
        "    setAllMediaVolumes(window.currentWebDisplaysVolume !== undefined ? window.currentWebDisplaysVolume : 1.0);" +
        "    var observer = new MutationObserver(function(mutations) {" +
        "      mutations.forEach(function(mutation) {" +
        "        mutation.addedNodes.forEach(function(node) {" +
        "          if (!node) return;" +
        "          if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') {" +
        "            if (window.currentWebDisplaysVolume !== undefined) {" +
        "              try { node.muted = false; node.volume = window.currentWebDisplaysVolume; } catch (e) {}" +
        "            }" +
        "          }" +
        "        });" +
        "      });" +
        "    });" +
        "    observer.observe(document.body, { childList: true, subtree: true });" +
        "    return true;" +
        "  }" +
        "  patchAudioContext(window.AudioContext);" +
        "  patchAudioContext(window.webkitAudioContext);" +
        "  if (!applyControl()) {" +
        "    if (document && document.addEventListener) {" +
        "      document.addEventListener('DOMContentLoaded', function() { applyControl(); });" +
        "    }" +
        "  }" +
        "  if (!window.__webdisplaysVolumePoll) {" +
        "    window.__webdisplaysVolumePoll = window.setInterval(function() {" +
        "      if (window.currentWebDisplaysVolume !== undefined) {" +
        "        window.setWebDisplaysVolume(window.currentWebDisplaysVolume);" +
        "      }" +
        "    }, 500);" +
        "  }" +
        "})();";
    
    /**
     * Initialize volume control for a browser.
     * This injects the JavaScript that will control audio/video elements.
     */
    public static void initializeBrowser(CefBrowser browser) {
        if (browser == null) return;
        
        try {
            executeOnAllFrames(browser, VOLUME_CONTROL_SCRIPT, "webdisplays://volume-control");
            Log.info("Initialized volume control for browser %d", browser.getIdentifier());
        } catch (Exception e) {
            Log.error("Failed to initialize volume control for browser %d: %s", 
                    browser.getIdentifier(), e.getMessage());
        }
    }
    
    /**
     * Update the volume of a browser based on screen settings and player distance.
     */
    public static void updateBrowserVolume(CefBrowser browser, ScreenData screen, ScreenBlockEntity blockEntity) {
        if (browser == null || screen == null) return;
        
        try {
            float volume = calculateVolume(screen, blockEntity);
            setBrowserVolume(browser, volume);
        } catch (Exception e) {
            Log.error("Failed to update browser volume: %s", e.getMessage());
        }
    }
    
    /**
     * Set the volume of a browser directly.
     */
    public static void setBrowserVolume(CefBrowser browser, float volume) {
        if (browser == null) return;
        
        // Clamp volume to [0, 1]
        volume = Math.max(0.0f, Math.min(1.0f, volume));
        
        try {
            String volStr = String.format(Locale.ROOT, "%.3f", volume);
            String script = VOLUME_CONTROL_SCRIPT +
                "; window.currentWebDisplaysVolume = " + volStr + "; " +
                "if (window.setWebDisplaysVolume) window.setWebDisplaysVolume(" + volStr + ");";
            executeOnAllFrames(browser, script, "webdisplays://set-volume");
        } catch (Exception e) {
            Log.error("Failed to set browser volume: %s", e.getMessage());
        }
    }

    private static void executeOnAllFrames(CefBrowser browser, String script, String origin) {
        try {
            browser.executeJavaScript(script, origin, 0);
        } catch (Exception ignored) {
        }

        try {
            for (Long id : browser.getFrameIdentifiers()) {
                try {
                    org.cef.browser.CefFrame frame = browser.getFrame(id);
                    if (frame != null && frame.isValid()) {
                        frame.executeJavaScript(script, origin, 0);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Calculate the volume based on screen settings and player distance.
     */
    private static float calculateVolume(ScreenData screen, ScreenBlockEntity blockEntity) {
        float baseVolume = screen.volume / 100.0f;
        
        if (!screen.autoVolume) {
            return baseVolume;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || blockEntity == null) {
            return baseVolume;
        }
        
        double dx = mc.player.getX() - (blockEntity.getBlockPos().getX() + 0.5);
        double dy = mc.player.getY() - (blockEntity.getBlockPos().getY() + 0.5);
        double dz = mc.player.getZ() - (blockEntity.getBlockPos().getZ() + 0.5);
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
     * Update all browser volumes. Called periodically from ClientProxy.
     */
    public static void updateAllBrowserVolumes() {
        ClientProxy proxy = (ClientProxy) WebDisplays.PROXY;
        java.util.HashMap<CefBrowser, Float> maxVolume = new java.util.HashMap<>();
        for (ScreenBlockEntity be : proxy.getScreens()) {
            for (int i = 0; i < be.screenCount(); i++) {
                ScreenData screen = be.getScreen(i);
                if (screen == null || screen.browser == null)
                    continue;

                float volume = calculateVolume(screen, be);
                Float current = maxVolume.get(screen.browser);
                if (current == null || volume > current)
                    maxVolume.put(screen.browser, volume);
            }
        }

        for (java.util.Map.Entry<CefBrowser, Float> entry : maxVolume.entrySet())
            setBrowserVolume(entry.getKey(), entry.getValue());
    }
}
