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

        ambilightColor = avgColor;
        ambilightGridColors = colors;
        ambilightGridSize = gridSize;
        ambilightSampleTimeMs = now;
        ambilightHasSample = true;
    }
}
