package net.montoyo.wd.utilities.browser;

import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.utilities.browser.handlers.js.queries.ElementCenterQuery;
import net.montoyo.wd.utilities.browser.handlers.js.JSQueryHandler;
import net.montoyo.wd.utilities.data.BlockSide;
import org.cef.browser.CefBrowser;

import java.awt.Rectangle;
import java.util.HashMap;
import java.nio.ByteBuffer;

public class WDClientBrowser extends MCEFBrowser implements WDBrowser {
    ElementCenterQuery focusedEl = new ElementCenterQuery("ActiveElement", "document.activeElement");
    ElementCenterQuery pointerLockEl =
            new ElementCenterQuery("PointerElement", "document.pointerLockElement")
                    .addAdditional("unadjust", "document.webdisplays__unadjustPointerMotion")
            ;
    HashMap<String, JSQueryHandler> handlerHashMap = new HashMap<>();

    ScreenBlockEntity be;
    BlockSide side;
    private volatile int ambilightColor = 0xFF000000;
    private volatile boolean ambilightHasSample = false;
    private volatile long ambilightSampleTimeMs = 0;
    private volatile int[] ambilightGridColors = null;
    private volatile int ambilightGridSize = 1;
    private volatile int[] ambilightSampleGridColors = null;
    private volatile int ambilightSampleGridSize = 0;
    private volatile int ambilightSampleWidth = 0;
    private volatile int ambilightSampleHeight = 0;

    public WDClientBrowser(MCEFClient client, String url, boolean transparent) {
        super(client, url, transparent);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        super.onPaint(browser, popup, dirtyRects, buffer, width, height);
        if (!popup) {
            updateAmbilight(buffer, width, height);
        }
    }

    public boolean hasAmbilightSample() {
        return ambilightHasSample;
    }

    public int getAmbilightColor() {
        return ambilightColor;
    }

    public int[] getAmbilightGridColors() {
        return ambilightGridColors;
    }

    public int getAmbilightGridSize() {
        return ambilightGridSize;
    }

    public int[] getAmbilightSampleGridColors() {
        return ambilightSampleGridColors;
    }

    public int getAmbilightSampleGridSize() {
        return ambilightSampleGridSize;
    }

    public int getAmbilightSampleWidth() {
        return ambilightSampleWidth;
    }

    public int getAmbilightSampleHeight() {
        return ambilightSampleHeight;
    }

    public int[] getAmbilightGridColorsForRect(int rectX, int rectY, int rectW, int rectH, int gridSize) {
        int[] sample = ambilightSampleGridColors;
        int sampleSize = ambilightSampleGridSize;
        int sampleWidth = ambilightSampleWidth;
        int sampleHeight = ambilightSampleHeight;
        if (sample == null || sampleSize < 1 || sampleWidth < 1 || sampleHeight < 1)
            return ambilightGridColors;

        if (gridSize < 1)
            return ambilightGridColors;

        if (rectW <= 0 || rectH <= 0)
            return ambilightGridColors;

        int[] out = new int[gridSize * gridSize];
        for (int gy = 0; gy < gridSize; gy++) {
            int cellY0 = rectY + (gy * rectH) / gridSize;
            int cellY1 = rectY + ((gy + 1) * rectH) / gridSize;
            if (cellY1 <= cellY0)
                cellY1 = cellY0 + 1;
            cellY0 = Math.max(0, Math.min(cellY0, sampleHeight - 1));
            cellY1 = Math.max(cellY0 + 1, Math.min(cellY1, sampleHeight));

            int sy0 = (cellY0 * sampleSize) / sampleHeight;
            int sy1 = (cellY1 * sampleSize) / sampleHeight;
            sy0 = Math.max(0, Math.min(sy0, sampleSize - 1));
            sy1 = Math.max(sy0 + 1, Math.min(sy1, sampleSize));

            for (int gx = 0; gx < gridSize; gx++) {
                int cellX0 = rectX + (gx * rectW) / gridSize;
                int cellX1 = rectX + ((gx + 1) * rectW) / gridSize;
                if (cellX1 <= cellX0)
                    cellX1 = cellX0 + 1;
                cellX0 = Math.max(0, Math.min(cellX0, sampleWidth - 1));
                cellX1 = Math.max(cellX0 + 1, Math.min(cellX1, sampleWidth));

                int sx0 = (cellX0 * sampleSize) / sampleWidth;
                int sx1 = (cellX1 * sampleSize) / sampleWidth;
                sx0 = Math.max(0, Math.min(sx0, sampleSize - 1));
                sx1 = Math.max(sx0 + 1, Math.min(sx1, sampleSize));

                long sumR = 0;
                long sumG = 0;
                long sumB = 0;
                int count = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    int row = sy * sampleSize;
                    for (int sx = sx0; sx < sx1; sx++) {
                        int color = sample[row + sx];
                        sumR += (color >> 16) & 0xFF;
                        sumG += (color >> 8) & 0xFF;
                        sumB += color & 0xFF;
                        count++;
                    }
                }
                if (count > 0) {
                    int r = (int) (sumR / count);
                    int g = (int) (sumG / count);
                    int b = (int) (sumB / count);
                    out[gy * gridSize + gx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                } else {
                    out[gy * gridSize + gx] = ambilightColor;
                }
            }
        }

        return out;
    }

    public long getAmbilightSampleTime() {
        return ambilightSampleTimeMs;
    }

    @Override
    public HashMap<String, JSQueryHandler> queryHandlers() {
        return handlerHashMap;
    }

    @Override
    public ElementCenterQuery focusedElement() {
        return focusedEl;
    }

    @Override
    public ElementCenterQuery pointerLockElement() {
        return pointerLockEl;
    }

    @Override
    public void setBe(ScreenBlockEntity blockEntity, BlockSide side) {
        this.be = blockEntity;
        this.side = side;
    }

    @Override
    public ScreenBlockEntity getBe() {
        return be;
    }

    @Override
    public BlockSide getSide() {
        return side;
    }

    private void updateAmbilight(ByteBuffer buffer, int width, int height) {
        if (!ClientConfig.Ambilight.enabled)
            return;

        if (buffer == null || width <= 0 || height <= 0)
            return;

        int intervalMs = ClientConfig.Ambilight.intervalMs;
        if (intervalMs < 1)
            intervalMs = 1;

        long now = System.currentTimeMillis();
        if (now - ambilightSampleTimeMs < intervalMs)
            return;

        int gridSize = ClientConfig.Ambilight.sourcesPerEdge;
        if (gridSize < 1)
            gridSize = 1;

        int cellCount = gridSize * gridSize;
        long[] sumR = new long[cellCount];
        long[] sumG = new long[cellCount];
        long[] sumB = new long[cellCount];
        int[] counts = new int[cellCount];

        int sampleSize = 32;
        if (width < sampleSize)
            sampleSize = width;
        if (height < sampleSize)
            sampleSize = height;
        if (sampleSize < 1)
            sampleSize = 1;
        int sampleCount = sampleSize * sampleSize;
        long[] sampleR = new long[sampleCount];
        long[] sampleG = new long[sampleCount];
        long[] sampleB = new long[sampleCount];
        int[] sampleCounts = new int[sampleCount];

        int stepX = Math.max(1, width / 32);
        int stepY = Math.max(1, height / 32);
        long totalR = 0;
        long totalG = 0;
        long totalB = 0;
        int totalCount = 0;

        for (int y = 0; y < height; y += stepY) {
            int rowIndex = y * width * 4;
            int cellY = (y * gridSize) / height;
            if (cellY >= gridSize)
                cellY = gridSize - 1;
            int sampleY = (y * sampleSize) / height;
            if (sampleY >= sampleSize)
                sampleY = sampleSize - 1;
            for (int x = 0; x < width; x += stepX) {
                int idx = rowIndex + (x * 4);
                int b = buffer.get(idx) & 0xFF;
                int g = buffer.get(idx + 1) & 0xFF;
                int r = buffer.get(idx + 2) & 0xFF;
                int cellX = (x * gridSize) / width;
                if (cellX >= gridSize)
                    cellX = gridSize - 1;
                int cellIndex = cellY * gridSize + cellX;
                sumR[cellIndex] += r;
                sumG[cellIndex] += g;
                sumB[cellIndex] += b;
                counts[cellIndex]++;
                totalR += r;
                totalG += g;
                totalB += b;
                totalCount++;

                int sampleX = (x * sampleSize) / width;
                if (sampleX >= sampleSize)
                    sampleX = sampleSize - 1;
                int sampleIndex = sampleY * sampleSize + sampleX;
                sampleR[sampleIndex] += r;
                sampleG[sampleIndex] += g;
                sampleB[sampleIndex] += b;
                sampleCounts[sampleIndex]++;
            }
        }

        if (totalCount <= 0)
            return;

        int avgR = (int) (totalR / totalCount);
        int avgG = (int) (totalG / totalCount);
        int avgB = (int) (totalB / totalCount);
        int avgColor = 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;

        int[] colors = new int[cellCount];
        for (int i = 0; i < cellCount; i++) {
            if (counts[i] > 0) {
                int r = (int) (sumR[i] / counts[i]);
                int g = (int) (sumG[i] / counts[i]);
                int b = (int) (sumB[i] / counts[i]);
                colors[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                colors[i] = avgColor;
            }
        }

        int[] sampleColors = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            if (sampleCounts[i] > 0) {
                int r = (int) (sampleR[i] / sampleCounts[i]);
                int g = (int) (sampleG[i] / sampleCounts[i]);
                int b = (int) (sampleB[i] / sampleCounts[i]);
                sampleColors[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                sampleColors[i] = avgColor;
            }
        }

        ambilightColor = avgColor;
        ambilightGridColors = colors;
        ambilightGridSize = gridSize;
        ambilightSampleGridColors = sampleColors;
        ambilightSampleGridSize = sampleSize;
        ambilightSampleWidth = width;
        ambilightSampleHeight = height;
        ambilightSampleTimeMs = now;
        ambilightHasSample = true;
    }
}
