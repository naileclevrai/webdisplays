package net.montoyo.wd.client.ambilight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.link.LinkedScreenGroup;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.browser.WDClientBrowser;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AmbilightController {
    private static final class ScreenKey {
        private final BlockPos pos;
        private final BlockSide side;
        private final int hash;

        private ScreenKey(BlockPos pos, BlockSide side) {
            this.pos = pos;
            this.side = side;
            this.hash = Objects.hash(pos, side);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof ScreenKey other))
                return false;
            return pos.equals(other.pos) && side == other.side;
        }
    }

    private static final class ScreenLights {
        private final List<LightEntry> lights = new ArrayList<>();
        private final int sizeX;
        private final int sizeY;
        private final float radius;
        private final int gridSize;
        private long lastSampleTime = 0;

        private ScreenLights(int sizeX, int sizeY, float radius, int gridSize) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.radius = radius;
            this.gridSize = gridSize;
        }

        private boolean needsRebuild(Vector2i size, float newRadius, int newGridSize) {
            return size.x != sizeX || size.y != sizeY || newRadius != radius || newGridSize != gridSize || lights.isEmpty();
        }
    }

    private static final class LightEntry {
        private final Object light;
        private final int colorIndex;
        private int currentColor;
        private int startColor;
        private int targetColor;
        private long startTimeMs;

        private LightEntry(Object light, int colorIndex, int initialColor, long startTimeMs) {
            this.light = light;
            this.colorIndex = colorIndex;
            this.currentColor = initialColor;
            this.startColor = initialColor;
            this.targetColor = initialColor;
            this.startTimeMs = startTimeMs;
        }
    }

    private final Map<ScreenKey, ScreenLights> lightsByScreen = new HashMap<>();

    public void tick(List<ScreenBlockEntity> screens, ClientProxy proxy) {
        if (!ClientConfig.Ambilight.enabled || ClientConfig.Ambilight.radius <= 0) {
            clear();
            return;
        }

        if (!ShimmerBridge.isAvailable()) {
            clear();
            return;
        }

        if (screens == null || screens.isEmpty()) {
            clear();
            return;
        }

        float radiusMultiplier = (float) ClientConfig.Ambilight.radius;
        int smoothMs = ClientConfig.Ambilight.smoothMs;
        long nowMs = System.currentTimeMillis();
        HashSet<ScreenKey> seen = new HashSet<>();

        for (ScreenBlockEntity te : screens) {
            if (te == null || !te.isLoaded())
                continue;

            for (int i = 0; i < te.screenCount(); i++) {
                ScreenData scr = te.getScreen(i);
                if (scr == null)
                    continue;

                ScreenKey key = new ScreenKey(te.getBlockPos(), scr.side);
                float radius = radiusMultiplier * Math.max(scr.size.x, scr.size.y);
                if (radius <= 0.0f) {
                    removeLights(key);
                    continue;
                }
                WDClientBrowser browser = (scr.browser instanceof WDClientBrowser wdBrowser) ? wdBrowser : null;
                if (browser == null || !browser.hasAmbilightSample()) {
                    removeLights(key);
                    continue;
                }

                int gridSize = browser.getAmbilightGridSize();
                int[] colors = browser.getAmbilightGridColors();
                if (scr.isLinked() && proxy != null) {
                    LinkedScreenGroup group = proxy.getLinkedGroup(te, scr);
                    LinkedScreenGroup.Entry entry = group != null ? group.getEntry(scr) : null;
                    if (group != null && entry != null) {
                        Vector2i groupSize = group.getSize();
                        int sampleWidth = browser.getAmbilightSampleWidth();
                        int sampleHeight = browser.getAmbilightSampleHeight();
                        if (groupSize != null && groupSize.x > 0 && groupSize.y > 0 && sampleWidth > 0 && sampleHeight > 0) {
                            double u0 = entry.offset.x / (double) groupSize.x;
                            double u1 = (entry.offset.x + scr.size.x) / (double) groupSize.x;
                            double v0 = 1.0 - (entry.offset.y + scr.size.y) / (double) groupSize.y;
                            double v1 = 1.0 - (entry.offset.y) / (double) groupSize.y;

                            int rectX = (int) Math.floor(u0 * sampleWidth);
                            int rectX2 = (int) Math.ceil(u1 * sampleWidth);
                            int rectY = (int) Math.floor(v0 * sampleHeight);
                            int rectY2 = (int) Math.ceil(v1 * sampleHeight);

                            rectX = Math.max(0, Math.min(rectX, sampleWidth - 1));
                            rectX2 = Math.max(rectX + 1, Math.min(rectX2, sampleWidth));
                            rectY = Math.max(0, Math.min(rectY, sampleHeight - 1));
                            rectY2 = Math.max(rectY + 1, Math.min(rectY2, sampleHeight));

                            int rectW = Math.max(1, rectX2 - rectX);
                            int rectH = Math.max(1, rectY2 - rectY);
                            colors = browser.getAmbilightGridColorsForRect(rectX, rectY, rectW, rectH, gridSize);
                        }
                    }
                }
                if (gridSize < 1 || colors == null || colors.length < gridSize * gridSize) {
                    removeLights(key);
                    continue;
                }

                Vector3i shapeOrigin = new Vector3i(te.getBlockPos());
                if (te.getLevel() != null && CommonConfig.Screen.keepShapeOnChange) {
                    Vector3i found = ScreenShape.findConnectedOrigin(
                        te.getLevel(),
                        te.getBlockPos(),
                        scr.side,
                        CommonConfig.Screen.maxScreenSizeX,
                        CommonConfig.Screen.maxScreenSizeY
                    );
                    if (found != null)
                        shapeOrigin = found;
                }
                ScreenShape.Data shape = ScreenShape.compute(te.getLevel(), shapeOrigin.toBlock(), scr.side, scr.size, scr.shapeMode, te.getBlockPos());
                int cellCount = gridSize * gridSize;
                for (int idx = 0; idx < cellCount; idx++) {
                    int gx = idx % gridSize;
                    int gy = idx / gridSize;
                    float localX = (gx + 0.5f) * scr.size.x / gridSize;
                    float localY = (gy + 0.5f) * scr.size.y / gridSize;
                    if (!ScreenShape.isInside(shape, scr.shapeMode, localX, localY)) {
                        colors[idx] = 0xFF000000;
                    }
                }

                seen.add(key);
                ScreenLights entry = lightsByScreen.get(key);
                if (entry == null || entry.needsRebuild(scr.size, radius, gridSize)) {
                    removeLights(key);
                    entry = buildLights(te, scr, colors, gridSize, radius, nowMs);
                    if (entry != null) {
                        entry.lastSampleTime = browser.getAmbilightSampleTime();
                        lightsByScreen.put(key, entry);
                    }
                    continue;
                }

                updateColor(entry, colors, browser.getAmbilightSampleTime(), nowMs, smoothMs);
            }
        }

        Iterator<Map.Entry<ScreenKey, ScreenLights>> it = lightsByScreen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ScreenKey, ScreenLights> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                removeLights(entry.getValue());
                it.remove();
            }
        }
    }

    public void clear() {
        for (ScreenLights entry : lightsByScreen.values())
            removeLights(entry);
        lightsByScreen.clear();
    }

    private void updateColor(ScreenLights entry, int[] colors, long sampleTime, long nowMs, int smoothMs) {
        if (sampleTime <= 0)
            return;
        boolean newSample = entry.lastSampleTime != sampleTime;
        if (newSample) {
            entry.lastSampleTime = sampleTime;
            for (LightEntry lightEntry : entry.lights) {
                if (lightEntry == null || lightEntry.light == null)
                    continue;
                int color = (lightEntry.colorIndex >= 0 && lightEntry.colorIndex < colors.length) ? colors[lightEntry.colorIndex] : 0xFF000000;
                if (smoothMs <= 0) {
                    lightEntry.currentColor = color;
                    lightEntry.startColor = color;
                    lightEntry.targetColor = color;
                    lightEntry.startTimeMs = nowMs;
                } else {
                    lightEntry.startColor = lightEntry.currentColor;
                    lightEntry.targetColor = color;
                    lightEntry.startTimeMs = nowMs;
                }
            }
        } else if (smoothMs <= 0) {
            return;
        }

        for (LightEntry lightEntry : entry.lights) {
            if (lightEntry == null || lightEntry.light == null)
                continue;
            if (ShimmerBridge.isRemoved(lightEntry.light))
                continue;
            int color;
            if (smoothMs <= 0) {
                color = lightEntry.targetColor;
                lightEntry.currentColor = color;
            } else {
                float t = (nowMs - lightEntry.startTimeMs) / (float) smoothMs;
                if (t >= 1.0f) {
                    color = lightEntry.targetColor;
                    lightEntry.currentColor = color;
                } else if (t <= 0.0f) {
                    color = lightEntry.startColor;
                    lightEntry.currentColor = color;
                } else {
                    color = lerpColor(lightEntry.startColor, lightEntry.targetColor, t);
                    lightEntry.currentColor = color;
                }
            }
            ShimmerBridge.setLightColor(lightEntry.light, color);
            ShimmerBridge.setEnable(lightEntry.light, true);
            ShimmerBridge.updateLight(lightEntry.light);
        }
    }

    private ScreenLights buildLights(ScreenBlockEntity te, ScreenData scr, int[] colors, int gridSize, float radius, long nowMs) {
        ScreenLights entry = new ScreenLights(scr.size.x, scr.size.y, radius, gridSize);

        Direction dir = Direction.values()[scr.side.ordinal()];
        float forwardOffset = (float) ClientConfig.Ambilight.offset;
        float offX = dir.getStepX() * forwardOffset;
        float offY = dir.getStepY() * forwardOffset;
        float offZ = dir.getStepZ() * forwardOffset;

        Vector3i base = new Vector3i(te.getBlockPos());
        float sizeX = (float) scr.size.x;
        float sizeY = (float) scr.size.y;

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (gridSize > 1 && x > 0 && x < gridSize - 1 && y > 0 && y < gridSize - 1)
                    continue;
                float centerX = ((x + 0.5f) * sizeX / gridSize) - 0.5f;
                float centerY = ((y + 0.5f) * sizeY / gridSize) - 0.5f;
                float px = base.x + 0.5f + (scr.side.right.x * centerX) + (scr.side.up.x * centerY) + offX;
                float py = base.y + 0.5f + (scr.side.right.y * centerX) + (scr.side.up.y * centerY) + offY;
                float pz = base.z + 0.5f + (scr.side.right.z * centerX) + (scr.side.up.z * centerY) + offZ;
                int colorIndex = y * gridSize + x;
                int color = (colorIndex >= 0 && colorIndex < colors.length) ? colors[colorIndex] : 0xFF000000;
                Object light = ShimmerBridge.addLight(new Vector3f(px, py, pz), color, radius);
                if (light != null)
                    entry.lights.add(new LightEntry(light, colorIndex, color, nowMs));
            }
        }

        if (entry.lights.isEmpty()) {
            removeLights(entry);
            return null;
        }

        return entry;
    }

    private static int lerpColor(int from, int to, float t) {
        int a0 = (from >> 24) & 0xFF;
        int r0 = (from >> 16) & 0xFF;
        int g0 = (from >> 8) & 0xFF;
        int b0 = from & 0xFF;

        int a1 = (to >> 24) & 0xFF;
        int r1 = (to >> 16) & 0xFF;
        int g1 = (to >> 8) & 0xFF;
        int b1 = to & 0xFF;

        int a = (int) (a0 + (a1 - a0) * t + 0.5f);
        int r = (int) (r0 + (r1 - r0) * t + 0.5f);
        int g = (int) (g0 + (g1 - g0) * t + 0.5f);
        int b = (int) (b0 + (b1 - b0) * t + 0.5f);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void removeLights(ScreenKey key) {
        ScreenLights entry = lightsByScreen.remove(key);
        if (entry != null)
            removeLights(entry);
    }

    private void removeLights(ScreenLights entry) {
        for (LightEntry light : entry.lights) {
            if (light != null && light.light != null)
                ShimmerBridge.removeLight(light.light);
        }
        entry.lights.clear();
    }
}
