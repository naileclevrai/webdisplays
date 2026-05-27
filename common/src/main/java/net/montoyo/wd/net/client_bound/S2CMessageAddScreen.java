/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.net.Packet;
import net.montoyo.wd.utilities.*;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.Rotation;
import net.montoyo.wd.utilities.data.ScreenLinkMode;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.serialization.NameUUIDPair;

import java.io.IOException;
import java.util.ArrayList;

import static net.montoyo.wd.block.ScreenBlock.hasTE;

public class S2CMessageAddScreen extends Packet {
	private boolean clear;
	private Vector3i pos;
	private ScreenData[] screens;
	
	public S2CMessageAddScreen(ScreenBlockEntity tes) {
		clear = true;
		pos = new Vector3i(tes.getBlockPos());
		screens = new ScreenData[tes.screenCount()];
		
		for (int i = 0; i < tes.screenCount(); i++)
			screens[i] = tes.getScreen(i);
	}
	
	public S2CMessageAddScreen(ScreenBlockEntity tes, ScreenData... toSend) {
		clear = false;
		pos = new Vector3i(tes.getBlockPos());
		screens = toSend;
	}
	
	public S2CMessageAddScreen(boolean clear, Vector3i pos, ScreenData[] screens) {
		this.clear = clear;
		this.pos = pos;
		this.screens = screens;
	}
	
	public S2CMessageAddScreen(FriendlyByteBuf buf) {
		super(buf);
		
		clear = buf.readBoolean();
		pos = new Vector3i(buf);
		
		int cnt = buf.readByte() & 7;
		
		screens = new ScreenData[cnt];
		for (int i = 0; i < cnt; i++) {
			screens[i] = new ScreenData();
			screens[i].side = BlockSide.values()[buf.readByte()];
			screens[i].size = new Vector2i(buf);
			screens[i].url = buf.readUtf();
			screens[i].resolution = new Vector2i(buf);
			screens[i].rotation = Rotation.values()[buf.readByte() & 3];
			int shapeMode = buf.readByte() & 7;
			ScreenShapeMode[] shapeModes = ScreenShapeMode.values();
			screens[i].shapeMode = (shapeMode >= 0 && shapeMode < shapeModes.length) ? shapeModes[shapeMode] : ScreenShapeMode.NONE;
			screens[i].linkId = buf.readUtf();
			screens[i].linkMode = ScreenLinkMode.values()[buf.readByte() & 1];
			screens[i].linkOrigin = buf.readBoolean();
			screens[i].owner = new NameUUIDPair(buf);
			screens[i].upgrades = new ArrayList<>();
			
			int numUpgrades = buf.readByte();
			for (int j = 0; j < numUpgrades; j++)
				screens[i].upgrades.add(buf.readItem());
		}
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(clear);
		pos.writeTo(buf);
		buf.writeByte(screens.length);
		
		for (ScreenData scr : screens) {
			buf.writeByte(scr.side.ordinal());
			scr.size.writeTo(buf);
			buf.writeUtf(scr.url);
			scr.resolution.writeTo(buf);
			buf.writeByte(scr.rotation.ordinal());
			buf.writeByte(scr.shapeMode.ordinal());
			buf.writeUtf(scr.linkId == null ? "" : scr.linkId);
			buf.writeByte(scr.linkMode.ordinal());
			buf.writeBoolean(scr.linkOrigin);
			scr.owner.writeTo(buf);
			buf.writeByte(scr.upgrades.size());
			
			for (ItemStack is : scr.upgrades)
				buf.writeItem(is);
		}
	}
	
	public void handle(NetworkEvent.Context ctx) {
		if (checkClient(ctx)) {
			ctx.enqueueWork(() -> {
				Level lvl = (Level) WebDisplays.PROXY.getWorld(ctx);
				BlockEntity te = lvl.getBlockEntity(pos.toBlock());
				if (!(te instanceof ScreenBlockEntity)) {
					lvl.setBlockAndUpdate(pos.toBlock(), lvl.getBlockState(pos.toBlock()).setValue(hasTE, true));
					te = lvl.getBlockEntity(pos.toBlock());
					
					if (!(te instanceof ScreenBlockEntity)) {
						if (clear)
							Log.error("CMessageAddScreen: Can't add screen to invalid tile entity at %s", pos.toString());
						
						return;
					}
				}
				
				ScreenBlockEntity tes = (ScreenBlockEntity) te;
				if (clear)
					tes.clear();
				
				for (ScreenData entry : screens) {
					ScreenData scr = tes.addScreen(entry.side, entry.size, entry.resolution, null, false);
					scr.rotation = entry.rotation;
					scr.shapeMode = entry.shapeMode;
					scr.linkId = entry.linkId;
					scr.linkMode = entry.linkMode;
					scr.linkOrigin = entry.linkOrigin;
					String webUrl;
					
					try {
						webUrl = ScreenBlockEntity.url(entry.url);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					
					scr.url = webUrl;
					scr.owner = entry.owner;
					scr.upgrades = entry.upgrades;
					
					if (scr.browser != null && (!scr.isLinked() || scr.linkOrigin))
						scr.browser.loadURL(webUrl);
				}
			});
			
			ctx.setPacketHandled(true);
		}
	}
}
