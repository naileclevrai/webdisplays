package net.montoyo.wd.utilities.link;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.client.link.LinkedScreenGroup;
import net.montoyo.wd.utilities.ScreenBlocks;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.math.Vector2i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Détecte les chaînes d'écrans en diagonale 45° (escalier) et calcule layout + arrondis aux joints.
 */
public final class DiagonalCornerHelper {
    /** Coin haut-gauche (joint avec la colonne précédente). */
    public static final int ROUND_TL = 1;
    /** Coin haut-droit (joint avec la colonne suivante). */
    public static final int ROUND_TR = 2;
    public static final int ROUND_BL = 4;
    public static final int ROUND_BR = 8;

    public static final String AUTO_LINK_PREFIX = "diag:";

    public static boolean isAutoDiagonalLinkId(String linkId) {
        return linkId != null && linkId.startsWith(AUTO_LINK_PREFIX);
    }

    private DiagonalCornerHelper() {
    }

    /**
     * Repère le coin bas-gauche d'une colonne d'écrans sur {@code side}.
     */
    public static BlockPos findColumnAnchor(BlockGetter level, BlockPos start, BlockSide side) {
        BlockState state = level.getBlockState(start);
        if (!(state.getBlock() instanceof ScreenBlock))
            return null;
        if (state.getBlock() instanceof ScreenBlock sb && sb.isFlatPanel())
            return null;

        BlockPos.MutableBlockPos bp = start.mutable();

        bp.move(side.down.x, side.down.y, side.down.z);
        while (ScreenBlocks.isScreen(level.getBlockState(bp)))
            bp.move(side.down.x, side.down.y, side.down.z);
        bp.move(side.up.x, side.up.y, side.up.z);

        bp.move(side.left.x, side.left.y, side.left.z);
        while (ScreenBlocks.isScreen(level.getBlockState(bp)))
            bp.move(side.left.x, side.left.y, side.left.z);
        bp.move(side.right.x, side.right.y, side.right.z);

        return bp.immutable();
    }

    /**
     * Colonnes d'un escalier 45° (décalage d'1 bloc en X et Z entre colonnes), même hauteur.
     */
    public static List<BlockPos> findDiagonalChain(BlockGetter level, BlockPos start, BlockSide side) {
        if (level.getBlockState(start).getBlock() instanceof ScreenBlock sb && sb.isFlatPanel())
            return List.of();

        BlockPos anchor = findColumnAnchor(level, start, side);
        if (anchor == null)
            return List.of();

        List<BlockPos> best = List.of();
        for (int stepX : new int[]{1, -1}) {
            for (int stepZ : new int[]{1, -1}) {
                List<BlockPos> chain = walkDiagonalChain(level, anchor, side, stepX, stepZ);
                if (chain.size() > best.size())
                    best = chain;
            }
        }

        return best.size() >= 2 ? best : List.of();
    }

    private static List<BlockPos> walkDiagonalChain(BlockGetter level, BlockPos anchor, BlockSide side, int stepX, int stepZ) {
        List<BlockPos> chain = new ArrayList<>();
        chain.add(anchor);

        BlockPos cur = anchor;
        while (true) {
            BlockPos next = cur.offset(stepX, 0, stepZ);
            if (!isColumnAnchor(level, next, side))
                break;
            chain.add(next);
            cur = next;
        }

        cur = anchor;
        while (true) {
            BlockPos prev = cur.offset(-stepX, 0, -stepZ);
            if (!isColumnAnchor(level, prev, side))
                break;
            chain.add(0, prev);
            cur = prev;
        }

        if (chain.size() < 2)
            return List.of();

        int height = measureColumnHeight(level, chain.get(0), side);
        if (height < 1)
            return List.of();

        for (BlockPos col : chain) {
            if (measureColumnHeight(level, col, side) != height)
                return List.of();
        }

        return chain;
    }

    public static int measureColumnHeight(BlockGetter level, BlockPos anchor, BlockSide side) {
        if (!isColumnAnchor(level, anchor, side))
            return 0;

        int h = 0;
        BlockPos.MutableBlockPos bp = anchor.mutable();
        while (ScreenBlocks.isScreen(level.getBlockState(bp))) {
            h++;
            bp.move(side.up.x, side.up.y, side.up.z);
        }
        return h;
    }

    private static boolean isColumnAnchor(BlockGetter level, BlockPos pos, BlockSide side) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ScreenBlock sb) || sb.isFlatPanel())
            return false;
        BlockPos anchor = findColumnAnchor(level, pos, side);
        return anchor != null && anchor.equals(pos);
    }

    public static boolean isDiagonalChain(List<LinkedScreenGroup.Entry> entries, BlockSide side) {
        if (entries == null || entries.size() < 2)
            return false;

        for (LinkedScreenGroup.Entry entry : entries) {
            if (entry.blockEntity.getBlockState().getBlock() instanceof ScreenBlock sb && sb.isFlatPanel())
                return false;
        }

        List<LinkedScreenGroup.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(e -> chainKey(e, side)));

        int stepX = 0;
        int stepZ = 0;

        for (int i = 1; i < sorted.size(); i++) {
            BlockPos prev = sorted.get(i - 1).blockEntity.getBlockPos();
            BlockPos cur = sorted.get(i).blockEntity.getBlockPos();
            int dx = cur.getX() - prev.getX();
            int dy = cur.getY() - prev.getY();
            int dz = cur.getZ() - prev.getZ();
            if (dy != 0 || !isUnitDiagonalStep(dx, dz))
                return false;

            if (i == 1) {
                stepX = dx;
                stepZ = dz;
            } else if (dx != stepX || dz != stepZ) {
                return false;
            }
        }

        return true;
    }

    private static boolean isUnitDiagonalStep(int dx, int dz) {
        return Math.abs(dx) == 1 && Math.abs(dz) == 1;
    }

    public static void applyDiagonalLayout(List<LinkedScreenGroup.Entry> entries, BlockSide side) {
        List<LinkedScreenGroup.Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(e -> chainKey(e, side)));

        int maxH = 1;
        for (int i = 0; i < sorted.size(); i++) {
            LinkedScreenGroup.Entry entry = sorted.get(i);
            entry.chainIndex = i;
            entry.offset.x = 0;
            entry.offset.y = 0;
            entry.curvedEdgeMask = 0;
            maxH = Math.max(maxH, entry.screen.size.y);
        }
    }

    /**
     * Coordonnée X dans le canvas diagonal (cisaillement 45°) pour une cellule locale.
     */
    public static float diagonalGlobalX(int chainIndex, int colWidth, int localX, int localY, int colHeight) {
        return chainIndex * (float) Math.max(1, colWidth) + localX + (colHeight - 1 - localY);
    }

    public static void mapDiagonalCellUv(int chainIndex, int colWidth, int colHeight, int cellX, int cellY,
                                         int canvasW, int canvasH, float[] uv) {
        float gx0 = diagonalGlobalX(chainIndex, colWidth, cellX, cellY, colHeight);
        float gx1 = gx0 + 1f;
        float invW = 1.0f / Math.max(1, canvasW);
        float invH = 1.0f / Math.max(1, canvasH);
        uv[0] = gx0 * invW;
        uv[1] = gx1 * invW;
        uv[2] = 1.0f - (cellY + 1) * invH;
        uv[3] = 1.0f - cellY * invH;
    }

    /**
     * Convertit une position normalisée locale (0..1) en coordonnées canvas diagonal.
     */
    public static void mapDiagonalHitToGroup(int chainIndex, int colWidth, int colHeight,
                                             double nx, double ny, int canvasW, int canvasH, Vector2i groupRes, Vector2i dst) {
        int localX = (int) Math.floor(nx * Math.max(1, colWidth));
        int localY = (int) Math.floor(ny * Math.max(1, colHeight));
        if (localX < 0)
            localX = 0;
        else if (localX >= colWidth)
            localX = colWidth - 1;
        if (localY < 0)
            localY = 0;
        else if (localY >= colHeight)
            localY = colHeight - 1;

        float gx = diagonalGlobalX(chainIndex, colWidth, localX, localY, colHeight) + (float) (nx * colWidth - localX);
        float gy = localY + (float) (ny * colHeight - localY);

        int baseX = canvasW > 0 ? (int) Math.round(gx / canvasW * groupRes.x) : 0;
        int baseY = canvasH > 0 ? (int) Math.round(gy / canvasH * groupRes.y) : 0;
        dst.x = Math.max(0, Math.min(groupRes.x, baseX));
        dst.y = Math.max(0, Math.min(groupRes.y, baseY));
    }

    public static int diagonalCanvasWidth(List<LinkedScreenGroup.Entry> entries) {
        int w = 0;
        int h = diagonalCanvasHeight(entries);
        for (LinkedScreenGroup.Entry e : entries)
            w += Math.max(1, e.screen.size.x);
        return Math.max(1, w + Math.max(0, h - 1));
    }

    public static int diagonalCanvasHeight(List<LinkedScreenGroup.Entry> entries) {
        int h = 1;
        for (LinkedScreenGroup.Entry e : entries)
            h = Math.max(h, e.screen.size.y);
        return h;
    }

    private static int chainKey(LinkedScreenGroup.Entry entry, BlockSide side) {
        BlockPos p = entry.blockEntity.getBlockPos();
        return p.getX() * side.right.x + p.getY() * side.right.y + p.getZ() * side.right.z
                + p.getX() * side.forward.x + p.getY() * side.forward.y + p.getZ() * side.forward.z;
    }
}
