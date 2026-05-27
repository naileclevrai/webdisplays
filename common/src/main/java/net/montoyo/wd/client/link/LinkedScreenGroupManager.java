package net.montoyo.wd.client.link;

import net.minecraft.resources.ResourceLocation;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.Rotation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class LinkedScreenGroupManager {
    public static final class LinkedScreenKey {
        public final String id;
        public final ResourceLocation dimension;
        public final BlockSide side;
        public final Rotation rotation;

        public LinkedScreenKey(String id, ResourceLocation dimension, BlockSide side, Rotation rotation) {
            this.id = id;
            this.dimension = dimension;
            this.side = side;
            this.rotation = rotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof LinkedScreenKey))
                return false;
            LinkedScreenKey other = (LinkedScreenKey) o;
            return Objects.equals(id, other.id)
                && Objects.equals(dimension, other.dimension)
                && side == other.side
                && rotation == other.rotation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, dimension, side, rotation);
        }
    }

    private final HashMap<LinkedScreenKey, LinkedScreenGroup> groups = new HashMap<>();
    private boolean dirty = true;

    /** Marque les groupes comme devant être recalculés au prochain tick. */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Reconstruit les groupes uniquement si un changement a été signalé,
     * puis resynchronise les browsers.
     * En dehors des changements structurels, on ne fait que syncBrowsers (léger).
     */
    public void rebuild(ClientProxy proxy) {
        if (dirty) {
            dirty = false;
            groups.clear();
            for (ScreenBlockEntity be : proxy.getScreens()) {
                if (be.getLevel() == null)
                    continue;

                ResourceLocation dimension = be.getLevel().dimension().location();
                for (int i = 0; i < be.screenCount(); i++) {
                    ScreenData scr = be.getScreen(i);
                    if (scr == null || !scr.isLinked())
                        continue;

                    LinkedScreenKey key = new LinkedScreenKey(scr.linkId, dimension, scr.side, scr.rotation);
                    LinkedScreenGroup group = groups.computeIfAbsent(key, LinkedScreenGroup::new);
                    group.add(be, scr);
                }
            }

            for (LinkedScreenGroup group : groups.values()) {
                group.buildLayout();
            }
            markAllGroupsNeedBrowserSync();
        }

        // Sync browsers à chaque tick (léger : propagation de ref)
        for (LinkedScreenGroup group : groups.values()) {
            group.syncBrowsersIfNeeded(proxy);
        }
    }

    public void markAllGroupsNeedBrowserSync() {
        for (LinkedScreenGroup group : groups.values())
            group.markNeedsBrowserSync();
    }

    public LinkedScreenGroup getGroup(ScreenBlockEntity be, ScreenData scr) {
        if (be == null || scr == null || !scr.isLinked() || be.getLevel() == null)
            return null;

        LinkedScreenKey key = new LinkedScreenKey(scr.linkId, be.getLevel().dimension().location(), scr.side, scr.rotation);
        return groups.get(key);
    }

    public Collection<LinkedScreenGroup> getGroups() {
        return groups.values();
    }

    public void clear() {
        groups.clear();
    }
}
