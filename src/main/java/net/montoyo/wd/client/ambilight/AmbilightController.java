package net.montoyo.wd.client.ambilight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
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

        private LightEntry(Object light, int colorIndex) {
            this.light = light;
            this.colorIndex = colorIndex;
        }
    }

    private final Map<ScreenKey, ScreenLights> lightsByScreen = new HashMap<>();

    public void tick(List<ScreenBlockEntity> screens) {
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

        float radius = (float) ClientConfig.Ambilight.radius;
        HashSet<ScreenKey> seen = new HashSet<>();

        for (ScreenBlockEntity te : screens) {
            if (te == null || !te.isLoaded())
                continue;

            for (int i = 0; i < te.screenCount(); i++) {
                ScreenData scr = te.getScreen(i);
                if (scr == null)
                    continue;

                ScreenKey key = new ScreenKey(te.getBlockPos(), scr.side);
                WDClientBrowser browser = (scr.browser instanceof WDClientBrowser wdBrowser) ? wdBrowser : null;
                if (browser == null || !browser.hasAmbilightSample()) {
                    removeLights(key);
                    continue;
                }

                int[] colors = browser.getAmbilightGridColors();
                int gridSize = browser.getAmbilightGridSize();
                if (gridSize < 1 || colors == null || colors.length < gridSize * gridSize) {
                    removeLights(key);
                    continue;
                }

                seen.add(key);
                ScreenLights entry = lightsByScreen.get(key);
                if (entry == null || entry.needsRebuild(scr.size, radius, gridSize)) {
                    removeLights(key);
                    entry = buildLights(te, scr, colors, gridSize, radius);
                    if (entry != null) {
                        entry.lastSampleTime = browser.getAmbilightSampleTime();
                        lightsByScreen.put(key, entry);
                    }
                    continue;
                }

                updateColor(entry, colors, browser.getAmbilightSampleTime());
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

    private void updateColor(ScreenLights entry, int[] colors, long sampleTime) {
        if (sampleTime <= 0)
            return;
        if (entry.lastSampleTime == sampleTime)
            return;
        entry.lastSampleTime = sampleTime;
        for (LightEntry lightEntry : entry.lights) {
            if (lightEntry == null || lightEntry.light == null)
                continue;
            if (ShimmerBridge.isRemoved(lightEntry.light))
                continue;
            int color = (lightEntry.colorIndex >= 0 && lightEntry.colorIndex < colors.length) ? colors[lightEntry.colorIndex] : 0xFF000000;
            ShimmerBridge.setLightColor(lightEntry.light, color);
            ShimmerBridge.setEnable(lightEntry.light, true);
            ShimmerBridge.updateLight(lightEntry.light);
        }
    }

    private ScreenLights buildLights(ScreenBlockEntity te, ScreenData scr, int[] colors, int gridSize, float radius) {
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
                    entry.lights.add(new LightEntry(light, colorIndex));
            }
        }

        if (entry.lights.isEmpty()) {
            removeLights(entry);
            return null;
        }

        return entry;
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
