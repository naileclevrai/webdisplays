package net.montoyo.wd.utilities.link;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.link.LinkedScreenGroup;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.client_bound.S2CMessageScreenUpdate;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.Rotation;
import net.montoyo.wd.utilities.math.Vector2i;

/**
 * Utilitaires partagés pour les groupes d'écrans liés (côté serveur et client).
 */
public final class LinkedScreenHelper {
    /** Rayon de recherche des écrans partageant un linkId (blocs chargés uniquement). */
    public static final int SEARCH_RADIUS = 256;

    private LinkedScreenHelper() {
    }

    public interface GroupVisitor {
        void accept(ScreenBlockEntity tes, ScreenData scr, BlockSide side);
    }

    public static void forEachInGroup(Level level, BlockPos near, String linkId, BlockSide side, Rotation rotation,
                                      GroupVisitor visitor) {
        if (level == null || near == null || linkId == null || linkId.isEmpty() || visitor == null)
            return;

        int minX = near.getX() - SEARCH_RADIUS;
        int maxX = near.getX() + SEARCH_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), near.getY() - SEARCH_RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, near.getY() + SEARCH_RADIUS);
        int minZ = near.getZ() - SEARCH_RADIUS;
        int maxZ = near.getZ() + SEARCH_RADIUS;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz))
                    continue;

                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ScreenBlockEntity tes))
                        continue;

                    BlockPos pos = be.getBlockPos();
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ)
                        continue;

                    visitMatchingScreens(tes, linkId, side, rotation, visitor);
                }
            }
        }
    }

    private static void visitMatchingScreens(ScreenBlockEntity tes, String linkId, BlockSide side, Rotation rotation,
                                             GroupVisitor visitor) {
        for (int i = 0; i < tes.screenCount(); i++) {
            ScreenData scr = tes.getScreen(i);
            if (scr == null || !linkId.equals(scr.linkId))
                continue;
            if (scr.side != side || scr.rotation != rotation)
                continue;
            visitor.accept(tes, scr, scr.side);
        }
    }

    private static boolean matchesGroup(ScreenData ref, ScreenData scr) {
        return scr != null && ref.linkId.equals(scr.linkId)
                && scr.side == ref.side && scr.rotation == ref.rotation;
    }

    public static boolean isLinkedSlave(ScreenData scr) {
        return scr != null && scr.isLinked() && !scr.linkOrigin;
    }

    public static ScreenData getOriginScreen(ClientProxy proxy, ScreenBlockEntity be, ScreenData scr) {
        if (scr == null || !scr.isLinked())
            return scr;
        if (scr.linkOrigin)
            return scr;

        LinkedScreenGroup group = proxy.getLinkedGroup(be, scr);
        if (group != null && group.getOrigin() != null && group.getOrigin().screen != null)
            return group.getOrigin().screen;
        return scr;
    }

    /** Affichage client : le flag peut ne pas être sync sur les slaves — on suit l'origin du groupe. */
    public static boolean isTestPatternVisible(ClientProxy proxy, ScreenBlockEntity be, ScreenData scr) {
        if (scr == null)
            return false;
        if (scr.testPattern)
            return true;
        if (!scr.isLinked() || proxy == null)
            return false;
        ScreenData origin = getOriginScreen(proxy, be, scr);
        return origin != null && origin != scr && origin.testPattern;
    }

    public static void propagateUrlFromOrigin(Level level, ScreenBlockEntity originTe, ScreenData originScr, String url) {
        if (originTe == null || originScr == null || !originScr.isLinked() || !originScr.linkOrigin)
            return;

        forEachInGroup(level, originTe.getBlockPos(), originScr.linkId, originScr.side, originScr.rotation,
                (tes, scr, side) -> {
                    if (tes == originTe && scr == originScr)
                        return;
                    scr.applyUrl(url);
                    tes.setChanged();
                    if (!level.isClientSide) {
                        WDNetworkRegistry.INSTANCE.send(
                                PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                S2CMessageScreenUpdate.setURL(tes, side, url)
                        );
                    }
                });
    }

    public static void propagateVolumeFromOrigin(Level level, ScreenBlockEntity originTe, ScreenData originScr,
                                                 float volume, boolean autoVolume) {
        if (originTe == null || originScr == null || !originScr.isLinked() || !originScr.linkOrigin)
            return;

        forEachInGroup(level, originTe.getBlockPos(), originScr.linkId, originScr.side, originScr.rotation,
                (tes, scr, side) -> {
                    if (tes == originTe && scr == originScr)
                        return;
                    scr.volume = volume;
                    scr.autoVolume = autoVolume;
                    tes.setChanged();
                    if (!level.isClientSide) {
                        WDNetworkRegistry.INSTANCE.send(
                                PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                S2CMessageScreenUpdate.volume(tes, side, volume, autoVolume)
                        );
                    }
                });
    }

    public static void propagateResolutionFromOrigin(Level level, ScreenBlockEntity originTe, ScreenData originScr,
                                                     Vector2i resolution) {
        if (originTe == null || originScr == null || !originScr.isLinked() || !originScr.linkOrigin)
            return;

        forEachInGroup(level, originTe.getBlockPos(), originScr.linkId, originScr.side, originScr.rotation,
                (tes, scr, side) -> {
                    if (tes == originTe && scr == originScr)
                        return;
                    scr.resolution = new Vector2i(resolution.x, resolution.y);
                    scr.clampResolution();
                    tes.setChanged();
                    if (!level.isClientSide) {
                        WDNetworkRegistry.INSTANCE.send(
                                PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                S2CMessageScreenUpdate.setResolution(tes, side, scr.resolution)
                        );
                    }
                });
    }

    /**
     * Quand l'écran principal d'un groupe est détruit, promeut le panneau restant le plus bas.
     */
    public static void promoteNewOriginOnServer(Level level, BlockPos removedPos, ScreenData removedScr,
                                                ScreenBlockEntity excludeTe, BlockSide excludeSide) {
        if (level == null || level.isClientSide || removedScr == null || !removedScr.isLinked() || !removedScr.linkOrigin)
            return;

        final ScreenBlockEntity[] newOriginTe = {null};
        final ScreenData[] newOriginScr = {null};
        final BlockSide[] newOriginSide = {null};
        final BlockPos[] bestPos = {null};

        forEachInGroup(level, removedPos, removedScr.linkId, removedScr.side, removedScr.rotation,
                (tes, scr, side) -> {
                    if (tes == excludeTe && side == excludeSide)
                        return;
                    BlockPos pos = tes.getBlockPos();
                    if (bestPos[0] == null || comparePos(pos, bestPos[0]) < 0) {
                        bestPos[0] = pos;
                        newOriginTe[0] = tes;
                        newOriginScr[0] = scr;
                        newOriginSide[0] = side;
                    }
                });

        if (newOriginTe[0] == null || newOriginScr[0] == null)
            return;

        final ScreenBlockEntity promotedTe = newOriginTe[0];
        final BlockSide promotedSide = newOriginSide[0];

        forEachInGroup(level, removedPos, removedScr.linkId, removedScr.side, removedScr.rotation,
                (tes, scr, side) -> {
                    if (tes == excludeTe && side == excludeSide)
                        return;

                    boolean isOrigin = tes == promotedTe && side == promotedSide;
                    if (scr.linkOrigin != isOrigin) {
                        scr.linkOrigin = isOrigin;
                        tes.setChanged();
                        WDNetworkRegistry.INSTANCE.send(
                                PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                S2CMessageScreenUpdate.link(tes, side, scr.linkId, scr.linkMode, isOrigin)
                        );
                    }

                    if (isOrigin) {
                        if (removedScr.url != null && !removedScr.url.equals(scr.url)) {
                            scr.applyUrl(removedScr.url);
                            WDNetworkRegistry.INSTANCE.send(
                                    PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                    S2CMessageScreenUpdate.setURL(tes, side, scr.url)
                            );
                        }
                        if (scr.volume != removedScr.volume || scr.autoVolume != removedScr.autoVolume) {
                            scr.volume = removedScr.volume;
                            scr.autoVolume = removedScr.autoVolume;
                            WDNetworkRegistry.INSTANCE.send(
                                    PacketDistributor.NEAR.with(() -> net.montoyo.wd.block.PeripheralBlock.point(level, tes.getBlockPos())),
                                    S2CMessageScreenUpdate.volume(tes, side, scr.volume, scr.autoVolume)
                            );
                        }
                    }
                    tes.setChanged();
                });
    }

    public static void propagateTestPatternFromOrigin(Level level, ScreenBlockEntity originTe, ScreenData originScr,
                                                     boolean enabled) {
        if (originTe == null || originScr == null || !originScr.isLinked() || !originScr.linkOrigin)
            return;

        forEachInGroup(level, originTe.getBlockPos(), originScr.linkId, originScr.side, originScr.rotation,
                (tes, scr, side) -> {
                    if (tes == originTe && scr == originScr)
                        return;
                    tes.applyTestPatternState(side, enabled);
                    tes.setChanged();
                });
    }

    /** Propagation immédiate côté client via les écrans déjà trackés (O(n), pas de scan de chunks). */
    public static void propagateTestPatternClient(ScreenBlockEntity originTe, ScreenData originScr, boolean enabled) {
        if (originTe == null || originScr == null || !originScr.isLinked() || !originScr.linkOrigin)
            return;
        if (!(WebDisplays.PROXY instanceof ClientProxy proxy))
            return;

        for (ScreenBlockEntity be : proxy.getScreens()) {
            if (be.getLevel() == null || be.getLevel() != originTe.getLevel())
                continue;
            for (int i = 0; i < be.screenCount(); i++) {
                ScreenData scr = be.getScreen(i);
                if (!matchesGroup(originScr, scr))
                    continue;
                be.applyTestPatternState(scr.side, enabled);
            }
        }
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX())
            return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY())
            return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }
}
