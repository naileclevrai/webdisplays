package net.montoyo.wd.entity;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.core.ScreenRights;
import net.montoyo.wd.utilities.*;
import net.montoyo.wd.utilities.browser.InWorldQueries;
import net.montoyo.wd.utilities.browser.WDBrowser;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.Rotation;
import net.montoyo.wd.utilities.data.ScreenLinkMode;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.serialization.NameUUIDPair;
import org.cef.browser.CefBrowser;

import java.util.ArrayList;
import java.util.UUID;

public class ScreenData {
    public BlockSide side;
    public Vector2i size;
    public Vector2i resolution;
    public Rotation rotation = Rotation.ROT_0;
    public String url;
    protected VideoType videoType;
    public NameUUIDPair owner;
    public ArrayList<NameUUIDPair> friends;
    public int friendRights;
    public int otherRights;
    public CefBrowser browser;
    public ArrayList<ItemStack> upgrades;
    public boolean doTurnOnAnim;
    public long turnOnTime;
    public Player laserUser;
    public final Vector2i lastMousePos = new Vector2i();
    public NibbleArray redstoneStatus; //null on client
    public boolean autoVolume = true;
    public float volume = 100.0f; // Volume percentage (0-100)
    public ScreenShapeMode shapeMode = ScreenShapeMode.NONE;
    public String linkId = "";
    public ScreenLinkMode linkMode = ScreenLinkMode.JOINED;
    public boolean linkOrigin = false;

    public int mouseType;

    public static ScreenData deserialize(CompoundTag tag) {
        ScreenData ret = new ScreenData();
        ret.side = BlockSide.values()[tag.getByte("Side")];
        ret.size = new Vector2i(tag.getInt("Width"), tag.getInt("Height"));
        ret.resolution = new Vector2i(tag.getInt("ResolutionX"), tag.getInt("ResolutionY"));
        ret.rotation = Rotation.values()[tag.getByte("Rotation")];
        ret.url = tag.getString("URL");
        ret.videoType = VideoType.getTypeFromURL(ret.url);

        if (ret.resolution.x <= 0 || ret.resolution.y <= 0) {
            float psx = ((float) ret.size.x) * 16.f - 4.f;
            float psy = ((float) ret.size.y) * 16.f - 4.f;
            psx *= 8.f; //TODO: Use ratio in config file
            psy *= 8.f;

            ret.resolution.x = (int) psx;
            ret.resolution.y = (int) psy;
        }

        if (tag.contains("OwnerName")) {
            String name = tag.getString("OwnerName");
            UUID uuid = tag.getUUID("OwnerUUID");
            ret.owner = new NameUUIDPair(name, uuid);
        }

        ListTag friends = tag.getList("Friends", 10);
        ret.friends = new ArrayList<>(friends.size());

        for (int i = 0; i < friends.size(); i++) {
            CompoundTag nf = friends.getCompound(i);
            NameUUIDPair pair = new NameUUIDPair(nf.getString("Name"), nf.getUUID("UUID"));
            ret.friends.add(pair);
        }

        ret.friendRights = tag.getByte("FriendRights");
        ret.otherRights = tag.getByte("OtherRights");

        ListTag upgrades = tag.getList("Upgrades", 10);
        ret.upgrades = new ArrayList<>();

        for (int i = 0; i < upgrades.size(); i++)
            ret.upgrades.add(ItemStack.of(upgrades.getCompound(i)));

        if (tag.contains("AutoVolume"))
            ret.autoVolume = tag.getBoolean("AutoVolume");

        if (tag.contains("Volume"))
            ret.volume = tag.getFloat("Volume");
        else
            ret.volume = 100.0f; // Default volume

        if (tag.contains("ShapeMode")) {
            byte mode = tag.getByte("ShapeMode");
            ScreenShapeMode[] modes = ScreenShapeMode.values();
            ret.shapeMode = (mode >= 0 && mode < modes.length) ? modes[mode] : ScreenShapeMode.NONE;
        } else {
            ret.shapeMode = ScreenShapeMode.NONE;
        }

        if (tag.contains("LinkId"))
            ret.linkId = tag.getString("LinkId");
        else
            ret.linkId = "";

        if (tag.contains("LinkMode"))
            ret.linkMode = ScreenLinkMode.values()[tag.getByte("LinkMode")];
        else
            ret.linkMode = ScreenLinkMode.JOINED;

        if (tag.contains("LinkOrigin"))
            ret.linkOrigin = tag.getBoolean("LinkOrigin");
        else
            ret.linkOrigin = false;

        return ret;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Side", (byte) side.ordinal());
        tag.putInt("Width", size.x);
        tag.putInt("Height", size.y);
        tag.putInt("ResolutionX", resolution.x);
        tag.putInt("ResolutionY", resolution.y);
        tag.putByte("Rotation", (byte) rotation.ordinal());
        tag.putString("URL", url);

        if (owner == null)
            Log.warning("Found TES with NO OWNER!!");
        else {
            tag.putString("OwnerName", owner.name);
            tag.putUUID("OwnerUUID", owner.uuid);
        }

        ListTag list = new ListTag();
        for (NameUUIDPair f : friends) {
            CompoundTag nf = new CompoundTag();
            nf.putString("Name", f.name);
            nf.putUUID("UUID", f.uuid);

            list.add(nf);
        }

        tag.put("Friends", list);
        tag.putByte("FriendRights", (byte) friendRights);
        tag.putByte("OtherRights", (byte) otherRights);

        list = new ListTag();
        for (ItemStack is : upgrades)
            list.add(is.save(new CompoundTag()));

        tag.put("Upgrades", list);
        tag.putBoolean("AutoVolume", autoVolume);
        tag.putFloat("Volume", volume);
        tag.putByte("ShapeMode", (byte) shapeMode.ordinal());
        tag.putString("LinkId", linkId == null ? "" : linkId);
        tag.putByte("LinkMode", (byte) linkMode.ordinal());
        tag.putBoolean("LinkOrigin", linkOrigin);
        return tag;
    }

    public boolean isLinked() {
        return linkId != null && !linkId.isEmpty();
    }

    public int rightsFor(Player ply) {
        return rightsFor(ply.getGameProfile().getId());
    }

    public int rightsFor(UUID uuid) {
        if (owner.uuid.equals(uuid))
            return ScreenRights.ALL;

        return friends.stream().anyMatch(f -> f.uuid.equals(uuid)) ? friendRights : otherRights;
    }

    public void setupRedstoneStatus(Level world, BlockPos start) {
        if (world.isClientSide()) {
            Log.warning("Called Screen.setupRedstoneStatus() on client.");
            return;
        }

        if (redstoneStatus != null) {
            Log.warning("Called Screen.setupRedstoneStatus() on server, but redstone status is non-null");
            return;
        }

        Direction[] VALUES = Direction.values();
        redstoneStatus = new NibbleArray(size.x * size.y);
        final Direction facing = VALUES[side.reverse().ordinal()];
        final ScreenIterator it = new ScreenIterator(start, side, size);

        while (it.hasNext()) {
            int idx = it.getIndex();
            redstoneStatus.set(idx, world.getSignal(it.next(), facing));
        }
    }


    public void clampResolution() {
        if (resolution.x > CommonConfig.Screen.maxResolutionX) {
            float newY = ((float) resolution.y) * ((float) CommonConfig.Screen.maxResolutionX) / ((float) resolution.x);
            resolution.x = CommonConfig.Screen.maxResolutionX;
            resolution.y = (int) newY;
        }

        if (resolution.y > CommonConfig.Screen.maxResolutionY) {
            float newX = ((float) resolution.x) * ((float) CommonConfig.Screen.maxResolutionY) / ((float) resolution.y);
            resolution.x = (int) newX;
            resolution.y = CommonConfig.Screen.maxResolutionY;
        }
    }

    public Vector2i getEffectiveResolution(Vector2i dst) {
        if (dst == null)
            dst = new Vector2i();

        double scale = ClientConfig.screenQualityScale;
        int scaledX = (int) Math.round(resolution.x * scale);
        int scaledY = (int) Math.round(resolution.y * scale);
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

        dst.x = scaledX;
        dst.y = scaledY;
        return dst;
    }

    public Vector2i scaleInputToEffective(Vector2i src, Vector2i dst) {
        if (src == null)
            return null;

        Vector2i effective = getEffectiveResolution(new Vector2i());
        int baseX = resolution.x;
        int baseY = resolution.y;
        int effX = effective.x;
        int effY = effective.y;

        if (rotation.isVertical) {
            baseX = resolution.y;
            baseY = resolution.x;
            effX = effective.y;
            effY = effective.x;
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

    public void refreshBrowser(ScreenBlockEntity be) {
        if (!(browser instanceof MCEFBrowser mcefBrowser))
            return;

        if (isLinked() && WebDisplays.PROXY instanceof ClientProxy proxy) {
            net.montoyo.wd.client.link.LinkedScreenGroup group = proxy.getLinkedGroup(be, this);
            if (group != null) {
                ScreenData origin = group.getOrigin() != null ? group.getOrigin().screen : null;
                if (origin != null && origin.browser instanceof MCEFBrowser originBrowser) {
                    group.resizeBrowser(originBrowser);
                } else {
                    group.resizeBrowser(mcefBrowser);
                }
                return;
            }
        }

        Vector2i effective = getEffectiveResolution(new Vector2i());
        if (rotation.isVertical)
            mcefBrowser.resize(effective.y, effective.x);
        else
            mcefBrowser.resize(effective.x, effective.y);
    }

    public void createBrowser(ScreenBlockEntity be, boolean doAnim) {
        if (WebDisplays.PROXY instanceof ClientProxy proxy) {
            if (isLinked()) {
                net.montoyo.wd.client.link.LinkedScreenGroup group = proxy.getLinkedGroup(be, this);
                if (group != null && group.getOrigin() != null) {
                    ScreenData origin = group.getOrigin().screen;
                    if (origin.browser == null) {
                        origin.createStandaloneBrowser(group.getOrigin().blockEntity, doAnim);
                    }

                    browser = origin.browser;
                    if (browser instanceof MCEFBrowser mcefBrowser) {
                        group.resizeBrowser(mcefBrowser);
                        mcefBrowser.setCursorChangeListener((type) -> proxy.updateCursorForBrowser(mcefBrowser, type));
                    }

                    doTurnOnAnim = doAnim;
                    turnOnTime = System.currentTimeMillis();
                    return;
                }
            }

            createStandaloneBrowser(be, doAnim);
        }
    }

    private void createStandaloneBrowser(ScreenBlockEntity be, boolean doAnim) {
        String finalUrl = WebDisplays.applyBlacklist(url != null ? url : "https://www.google.com");

        // Use browser pooling - screens can share browsers if they display the same URL
        // Create a unique identifier for this screen
        Object screenId = be.getBlockPos().toString() + "_" + side.toString();
        browser = WDBrowser.createBrowserFromPool(finalUrl, false, true, screenId);

        // set screen
        if (browser instanceof MCEFBrowser mcefBrowser) {
            Vector2i effective = getEffectiveResolution(new Vector2i());
            if (rotation.isVertical)
                mcefBrowser.resize(effective.y, effective.x);
            else
                mcefBrowser.resize(effective.x, effective.y);

            mcefBrowser.setCursorChangeListener((type) -> mouseType = type);
        }

        // setup screen as in world
        if (browser instanceof WDBrowser wdBrowser) {
            InWorldQueries.attach(be, side, wdBrowser);
        }

        // Initialize browser-side volume control for OS playback path
        net.montoyo.wd.client.audio.BrowserVolumeManager.initializeBrowser(browser);
        net.montoyo.wd.client.audio.BrowserVolumeManager.updateBrowserVolume(browser, this, be);

        doTurnOnAnim = doAnim;
        turnOnTime = System.currentTimeMillis();
    }

    /**
     * Release the browser back to the pool when the screen is destroyed or unloaded.
     * @param be The screen block entity
     */
    public void releaseBrowser(ScreenBlockEntity be) {
        if (browser != null && WebDisplays.PROXY instanceof ClientProxy proxy) {
            if (isLinked()) {
                net.montoyo.wd.client.link.LinkedScreenGroup group = proxy.getLinkedGroup(be, this);
                if (group != null && group.getOrigin() != null) {
                    ScreenData origin = group.getOrigin().screen;
                    if (origin != null && origin != this) {
                        if (origin.browser == browser) {
                            browser = null;
                            return;
                        }
                    } else if (origin == this) {
                        group.clearBrowserRefs();
                    }
                }
            }

            Object screenId = be.getBlockPos().toString() + "_" + side.toString();
            WDBrowser.releaseBrowser(browser, screenId);
            browser = null;
        }
    }
}
