package net.montoyo.wd.client.link;

import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.link.DiagonalCornerHelper;
import net.montoyo.wd.utilities.data.Rotation;
import net.montoyo.wd.utilities.data.ScreenLinkMode;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LinkedScreenGroup {
    public static final class Entry {
        public final ScreenBlockEntity blockEntity;
        public final ScreenData screen;
        public int rawX;
        public int rawY;
        public int chainIndex;
        public final Vector2i offset = new Vector2i();
        /** {@link DiagonalCornerHelper#ROUND_TL} / {@link DiagonalCornerHelper#ROUND_TR} aux joints */
        public int curvedEdgeMask;

        private Entry(ScreenBlockEntity blockEntity, ScreenData screen) {
            this.blockEntity = blockEntity;
            this.screen = screen;
        }
    }

    private final LinkedScreenGroupManager.LinkedScreenKey key;
    private final List<Entry> entries = new ArrayList<>();
    private final IdentityHashMap<ScreenData, Entry> entryMap = new IdentityHashMap<>();
    private Entry origin;
    private ScreenLinkMode mode = ScreenLinkMode.JOINED;
    private Vector2i size = new Vector2i(1, 1);
    private Vector2i resolution = new Vector2i(1, 1);
    private Vector2i effectiveResolution = new Vector2i(1, 1);
    private boolean diagonalLayout;
    private int lastBrowserWidth = -1;
    private int lastBrowserHeight = -1;
    private boolean needsBrowserSync = true;

    public LinkedScreenGroup(LinkedScreenGroupManager.LinkedScreenKey key) {
        this.key = key;
    }

    public void add(ScreenBlockEntity blockEntity, ScreenData screen) {
        Entry entry = new Entry(blockEntity, screen);
        entries.add(entry);
        entryMap.put(screen, entry);
    }

    public void markNeedsBrowserSync() {
        needsBrowserSync = true;
    }

    public void buildLayout() {
        if (entries.isEmpty())
            return;

        origin = selectOrigin();
        if (origin != null && origin.screen.linkMode != null)
            mode = origin.screen.linkMode;

        BlockSide side = key.side;
        for (Entry entry : entries) {
            BlockPos pos = entry.blockEntity.getBlockPos();
            Vector3i shapeOrigin = new Vector3i(pos);
            if (entry.blockEntity.getLevel() != null && CommonConfig.Screen.keepShapeOnChange) {
                Vector3i found = ScreenShape.findConnectedOrigin(
                    entry.blockEntity.getLevel(),
                    pos,
                    entry.screen.side,
                    CommonConfig.Screen.maxScreenSizeX,
                    CommonConfig.Screen.maxScreenSizeY
                );
                if (found != null)
                    shapeOrigin = found;
            }
            entry.rawX = shapeOrigin.x * side.right.x + shapeOrigin.y * side.right.y + shapeOrigin.z * side.right.z;
            entry.rawY = shapeOrigin.x * side.up.x + shapeOrigin.y * side.up.y + shapeOrigin.z * side.up.z;
        }

        if (DiagonalCornerHelper.isDiagonalChain(entries, side)) {
            DiagonalCornerHelper.applyDiagonalLayout(entries, side);
            size = new Vector2i(
                    DiagonalCornerHelper.diagonalCanvasWidth(entries),
                    DiagonalCornerHelper.diagonalCanvasHeight(entries)
            );
            diagonalLayout = true;
        } else {
            diagonalLayout = false;
            if (mode == ScreenLinkMode.SPACED) {
                computeSpacedLayout();
            } else {
                computeJoinedLayout();
            }
        }

        computeResolution();
        needsBrowserSync = true;
    }

    public Entry getEntry(ScreenData screen) {
        return entryMap.get(screen);
    }

    public Entry getOrigin() {
        return origin;
    }

    public Vector2i getSize() {
        return size;
    }

    public boolean isDiagonalLayout() {
        return diagonalLayout;
    }

    public java.util.List<Entry> getEntriesSorted() {
        java.util.List<Entry> sorted = new java.util.ArrayList<>(entries);
        if (diagonalLayout)
            sorted.sort(java.util.Comparator.comparingInt(e -> e.chainIndex));
        return sorted;
    }

    public Vector2i getResolution() {
        return resolution;
    }

    public Vector2i getEffectiveResolution(Vector2i dst) {
        if (dst == null)
            dst = new Vector2i();
        dst.x = effectiveResolution.x;
        dst.y = effectiveResolution.y;
        return dst;
    }

    public void resizeBrowser(MCEFBrowser browser) {
        if (browser == null)
            return;

        int width = effectiveResolution.x;
        int height = effectiveResolution.y;
        if (key.rotation.isVertical) {
            int tmp = width;
            width = height;
            height = tmp;
        }

        if (width < 1)
            width = 1;
        if (height < 1)
            height = 1;

        if (width != lastBrowserWidth || height != lastBrowserHeight) {
            browser.resize(width, height);
            lastBrowserWidth = width;
            lastBrowserHeight = height;
        }
    }

    public Vector2i scaleInputToEffective(Vector2i src, Rotation rotation, Vector2i dst) {
        if (src == null)
            return null;

        int baseX = resolution.x;
        int baseY = resolution.y;
        int effX = effectiveResolution.x;
        int effY = effectiveResolution.y;

        if (rotation.isVertical) {
            baseX = resolution.y;
            baseY = resolution.x;
            effX = effectiveResolution.y;
            effY = effectiveResolution.x;
        }

        if (baseX <= 0 || baseY <= 0 || (baseX == effX && baseY == effY)) {
            return src;
        }

        if (dst == null)
            dst = new Vector2i();

        double scaleX = effX / (double) baseX;
        double scaleY = effY / (double) baseY;

        int scaledX = (int) Math.round(src.x * scaleX);
        int scaledY = (int) Math.round(src.y * scaleY);

        if (scaledX < 0)
            scaledX = 0;
        else if (scaledX > effX)
            scaledX = effX;

        if (scaledY < 0)
            scaledY = 0;
        else if (scaledY > effY)
            scaledY = effY;

        dst.x = scaledX;
        dst.y = scaledY;
        return dst;
    }

    public void clearBrowserRefs() {
        for (Entry entry : entries)
            entry.screen.browser = null;
    }

    public void updateMouseType(int cursorType) {
        for (Entry entry : entries)
            entry.screen.mouseType = cursorType;
    }

    /**
     * Point d'entrée unique pour créer/emprunter le browser partagé du groupe.
     */
    public void ensureGroupBrowser(ClientProxy proxy) {
        if (entries.isEmpty() || origin == null || origin.screen == null)
            return;

        ScreenData originScreen = origin.screen;
        if (originScreen.browser == null) {
            double dist = WebDisplays.PROXY.distanceTo(
                    origin.blockEntity,
                    Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition()
            );
            if (dist <= WebDisplays.INSTANCE.loadDistance2 * 16)
                originScreen.ensureStandaloneBrowser(origin.blockEntity, true);
        }

        if (originScreen.browser == null)
            return;

        syncBrowsers(proxy);
    }

    public void syncBrowsersIfNeeded(ClientProxy proxy) {
        if (!needsBrowserSync && browsersInSync())
            return;
        syncBrowsers(proxy);
    }

    private boolean browsersInSync() {
        if (origin == null || origin.screen == null || origin.screen.browser == null)
            return true;

        org.cef.browser.CefBrowser shared = origin.screen.browser;
        for (Entry entry : entries) {
            if (entry.screen == null || entry.screen == origin.screen)
                continue;
            if (entry.screen.browser != shared)
                return false;
        }
        return true;
    }

    public void syncBrowsers(ClientProxy proxy) {
        if (entries.isEmpty() || origin == null)
            return;

        ScreenData originScreen = origin.screen;
        if (originScreen == null)
            return;

        if (originScreen.browser == null) {
            needsBrowserSync = true;
            return;
        }

        for (Entry entry : entries) {
            if (entry.screen == null || entry.screen == originScreen)
                continue;

            if (entry.screen.browser != originScreen.browser) {
                if (entry.screen.browser != null && entry.screen.browser != originScreen.browser)
                    entry.screen.releaseBrowser(entry.blockEntity);
                entry.screen.browser = originScreen.browser;
            }
        }

        if (originScreen.browser instanceof MCEFBrowser mcefBrowser) {
            if (proxy != null)
                mcefBrowser.setCursorChangeListener((type) -> proxy.updateCursorForBrowser(mcefBrowser, type));
            resizeBrowser(mcefBrowser);
        }

        needsBrowserSync = false;
    }

    /**
     * Appelé quand l'origin est détruit : retire les refs slaves et promeut un nouvel origin côté client.
     */
    public void onOriginDestroyed(ClientProxy proxy) {
        ScreenData destroyedOrigin = origin != null ? origin.screen : null;
        for (Entry entry : entries) {
            if (entry.screen == null || entry.screen == destroyedOrigin)
                continue;
            entry.screen.browser = null;
        }

        promoteNewOriginClient(proxy, destroyedOrigin);
    }

    private void promoteNewOriginClient(ClientProxy proxy, ScreenData destroyedOrigin) {
        List<Entry> remaining = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.screen == null || entry.screen == destroyedOrigin)
                continue;
            if (entry.blockEntity.getScreen(entry.screen.side) == null)
                continue;
            remaining.add(entry);
        }

        if (remaining.isEmpty())
            return;

        remaining.sort(Comparator.comparing((Entry e) -> e.blockEntity.getBlockPos(), LinkedScreenGroup::comparePos));
        Entry newOriginEntry = remaining.get(0);
        for (Entry entry : remaining)
            entry.screen.linkOrigin = entry == newOriginEntry;

        origin = newOriginEntry;
        needsBrowserSync = true;
        if (proxy != null)
            proxy.notifyLinkChanged();
    }

    private Entry selectOrigin() {
        List<Entry> origins = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.screen.linkOrigin)
                origins.add(entry);
        }

        if (!origins.isEmpty()) {
            origins.sort(Comparator.comparing((Entry e) -> e.blockEntity.getBlockPos(), LinkedScreenGroup::comparePos));
            return origins.get(0);
        }

        entries.sort(Comparator.comparing((Entry e) -> e.blockEntity.getBlockPos(), LinkedScreenGroup::comparePos));
        return entries.get(0);
    }

    private void computeSpacedLayout() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Entry entry : entries) {
            minX = Math.min(minX, entry.rawX);
            minY = Math.min(minY, entry.rawY);
            maxX = Math.max(maxX, entry.rawX + entry.screen.size.x);
            maxY = Math.max(maxY, entry.rawY + entry.screen.size.y);
        }

        size = new Vector2i(maxX - minX, maxY - minY);
        for (Entry entry : entries) {
            entry.offset.x = entry.rawX - minX;
            entry.offset.y = entry.rawY - minY;
        }
    }

    private void computeJoinedLayout() {
        TreeMap<Integer, Integer> colWidths = new TreeMap<>();
        TreeMap<Integer, Integer> rowHeights = new TreeMap<>();

        for (Entry entry : entries) {
            colWidths.merge(entry.rawX, entry.screen.size.x, Math::max);
            rowHeights.merge(entry.rawY, entry.screen.size.y, Math::max);
        }

        Map<Integer, Integer> packedX = new TreeMap<>();
        Map<Integer, Integer> packedY = new TreeMap<>();
        int cursor = 0;
        for (Map.Entry<Integer, Integer> col : colWidths.entrySet()) {
            packedX.put(col.getKey(), cursor);
            cursor += col.getValue();
        }
        int width = cursor;

        cursor = 0;
        for (Map.Entry<Integer, Integer> row : rowHeights.entrySet()) {
            packedY.put(row.getKey(), cursor);
            cursor += row.getValue();
        }
        int height = cursor;

        size = new Vector2i(width, height);
        for (Entry entry : entries) {
            entry.offset.x = packedX.get(entry.rawX);
            entry.offset.y = packedY.get(entry.rawY);
        }
    }

    private void computeResolution() {
        ScreenData ref = origin != null ? origin.screen : entries.get(0).screen;
        int baseX = ref.resolution.x;
        int baseY = ref.resolution.y;
        if (ref.size.x > 0)
            baseX = Math.round((ref.resolution.x / (float) ref.size.x) * size.x);
        if (ref.size.y > 0)
            baseY = Math.round((ref.resolution.y / (float) ref.size.y) * size.y);

        if (baseX < 1)
            baseX = 1;
        if (baseY < 1)
            baseY = 1;

        resolution = new Vector2i(baseX, baseY);
        effectiveResolution = computeEffectiveResolution(resolution);
    }

    private static Vector2i computeEffectiveResolution(Vector2i base) {
        double scale = ClientConfig.screenQualityScale;
        int scaledX = (int) Math.round(base.x * scale);
        int scaledY = (int) Math.round(base.y * scale);
        if (scaledX < 1)
            scaledX = 1;
        if (scaledY < 1)
            scaledY = 1;

        if (scaledX > CommonConfig.Screen.maxResolutionX) {
            float newY = ((float) scaledY) * ((float) CommonConfig.Screen.maxResolutionX) / ((float) scaledX);
            scaledX = CommonConfig.Screen.maxResolutionX;
            scaledY = (int) newY;
        }

        if (scaledY > CommonConfig.Screen.maxResolutionY) {
            float newX = ((float) scaledX) * ((float) CommonConfig.Screen.maxResolutionY) / ((float) scaledY);
            scaledX = (int) newX;
            scaledY = CommonConfig.Screen.maxResolutionY;
        }

        return new Vector2i(scaledX, scaledY);
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX())
            return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY())
            return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }
}
