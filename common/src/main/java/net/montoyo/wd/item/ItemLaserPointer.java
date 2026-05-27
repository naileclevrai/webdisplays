/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.montoyo.wd.block.ScreenBlock;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;
import net.montoyo.wd.controls.builtin.ClickControl;
import net.montoyo.wd.core.DefaultUpgrade;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl;
import net.montoyo.wd.registry.BlockRegistry;
import net.montoyo.wd.utilities.Multiblock;
import net.montoyo.wd.utilities.ScreenBlocks;
import net.montoyo.wd.utilities.ScreenShape;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.math.Vector2i;
import net.montoyo.wd.utilities.math.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemLaserPointer extends Item implements WDItem {
	
	public ItemLaserPointer(Properties properties) {
		super(properties
				.stacksTo(1)
//				.tab(WebDisplays.CREATIVE_TAB)
		);
	}
	
	//Laser pointer
	private static ScreenBlockEntity pointedScreen;
	private static BlockSide pointedScreenSide;
	private static long lastPointPacket;
	
	private static boolean mouseDown = false;
	private static boolean left;
	private static boolean middle;
	private static boolean right;
	
	public static void tick(Minecraft mc) {
		BlockHitResult result = ClientProxy.raycast(64.0); //TODO: Make that distance configurable
		
		BlockPos bpos = result.getBlockPos();
		
		if (result.getType() == HitResult.Type.BLOCK && ScreenBlocks.isScreen(mc.level.getBlockState(bpos))) {
			Vector3i shapePos = new Vector3i(result.getBlockPos());
			BlockSide side = BlockSide.values()[result.getDirection().ordinal()];
			Vector3i tePos = null;

			if (CommonConfig.Screen.keepShapeOnChange) {
				shapePos = ScreenShape.findConnectedOrigin(mc.level, result.getBlockPos(), side, CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY);
				tePos = ScreenShape.findConnectedScreenEntity(mc.level, result.getBlockPos(), side, CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY);
				if (tePos == null)
					return;
			} else {
				Multiblock.findOrigin(mc.level, shapePos, side, null);
				tePos = shapePos;
			}
			ScreenBlockEntity te = (ScreenBlockEntity) mc.level.getBlockEntity(tePos.toBlock());
			
			if (te != null && te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) { //hasUpgrade returns false is there's no screen on side 'side'
				//Since rights aren't synchronized, let the server check them for us...
				ScreenData scr = te.getScreen(side);
				
				if (scr.browser != null) {
					float hitX = ((float) result.getLocation().x) - (float) shapePos.x;
					float hitY = ((float) result.getLocation().y) - (float) shapePos.y;
					float hitZ = ((float) result.getLocation().z) - (float) shapePos.z;
					Vector2i tmp = new Vector2i();
					
					if (ScreenBlock.hit2pixels(side, mc.level, shapePos.toBlock(), tePos.toBlock(), scr, hitX, hitY, hitZ, tmp)) {
						laserClick(te, side, scr, tmp);
					}
				}
			}
		}
	}
	
	public static void deselect(Minecraft mc) {
		deselectScreen();
	}
	
	private static void laserClick(ScreenBlockEntity tes, BlockSide side, ScreenData scr, Vector2i hit) {
		tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
		if (pointedScreen == tes && pointedScreenSide == side) {
			long t = System.currentTimeMillis();
			
			if (t - lastPointPacket >= 100) {
				lastPointPacket = t;
				WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserMove(tes, side, hit));
			}
		} else {
			deselectScreen();
			pointedScreen = tes;
			pointedScreenSide = side;
		}
	}
	
	private static void deselectScreen() {
		if (pointedScreen != null && pointedScreenSide != null) {
			pointedScreen = null;
			pointedScreenSide = null;
		}
	}
	
	public static void press(boolean press, int button) {
		if (button <= 1 && ClientConfig.Input.switchButtons)
			button = 1 - button;
		
		if (button == 0) left = press;
		else if (button == 1) right = press;
		else if (button == 2) middle = press;
		
		Minecraft mc = Minecraft.getInstance();
		
		BlockHitResult result = ClientProxy.raycast(64.0); //TODO: Make that distance configurable
		Vector3i pos = new Vector3i(result.getBlockPos());
		BlockSide side = BlockSide.values()[result.getDirection().ordinal()];
		if (CommonConfig.Screen.keepShapeOnChange) {
			pos = ScreenShape.findConnectedScreenEntity(mc.level, result.getBlockPos(), side, CommonConfig.Screen.maxScreenSizeX, CommonConfig.Screen.maxScreenSizeY);
			if (pos == null)
				return;
		} else {
			Multiblock.findOrigin(mc.level, pos, side, null);
		}
		
		BlockEntity be = mc.level.getBlockEntity(pos.toBlock());
		if (!(be instanceof ScreenBlockEntity)) return;
		
		//noinspection PatternVariableCanBeUsed
		ScreenBlockEntity te = (ScreenBlockEntity) be;
		
		if (te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) { //hasUpgrade returns false is there's no screen on side 'side'
			int finalButton = button;
			te.interact(result, (hit) -> {
				te.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
				te.handleMouseEvent(side, press ? ClickControl.ControlType.DOWN : ClickControl.ControlType.UP, hit, finalButton);

				if (press)
					WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserDown(te, side, hit, finalButton));
				else
					WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(te, side, finalButton));
			});
		}
	}
	
	public static boolean isOn() {
		return left || right || middle;
	}
	
	@Nullable
	@Override
	public String getWikiName(@Nonnull ItemStack is) {
		return is.getItem().getName(is).getString();
	}
}
