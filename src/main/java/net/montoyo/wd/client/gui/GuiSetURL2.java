/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.controls.Button;
import net.montoyo.wd.client.gui.controls.CheckBox;
import net.montoyo.wd.client.gui.controls.TextField;
import net.montoyo.wd.client.gui.loading.FillControl;
import net.montoyo.wd.controls.builtin.VolumeControl;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.item.ItemMinePad2;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl;
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.serialization.Util;
import net.montoyo.wd.utilities.math.Vector3i;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class GuiSetURL2 extends WDScreen {
	
	//Screen data
	private ScreenBlockEntity tileEntity;
	private BlockSide screenSide;
	private Vector3i remoteLocation;
	
	//Pad data
	private ItemStack stack;
	private final boolean isPad;
	
	//Common
	private final String screenURL;
	
	@FillControl
	private TextField tfURL;

	@FillControl
	private TextField tfVolume;

	@FillControl
	private CheckBox cbAutoVolume;

	@FillControl
	private Button btnShutDown;

	@FillControl
	private Button btnCancel;

	@FillControl
	private Button btnOk;
	
	public GuiSetURL2(ScreenBlockEntity tes, BlockSide side, String url, Vector3i rl) {
		super(Component.nullToEmpty(null));
		tileEntity = tes;
		screenSide = side;
		remoteLocation = rl;
		isPad = false;
		screenURL = url;
	}
	
	public GuiSetURL2(ItemStack is, String url) {
		super(Component.nullToEmpty(null));
		isPad = true;
		stack = is;
		screenURL = url;
	}
	
	@Override
	public void init() {
		super.init();
		loadFrom(new ResourceLocation("webdisplays", "gui/seturl.json"));
		tfURL.setText(screenURL);

		// Initialize volume controls for screens (not pads)
		if (!isPad && tileEntity != null) {
			ScreenData screen = tileEntity.getScreen(screenSide);
			if (screen != null) {
				tfVolume.setText(String.valueOf((int) screen.volume));
				cbAutoVolume.setChecked(screen.autoVolume);
			}
		}
	}
	
	@Override
	protected void addLoadCustomVariables(Map<String, Double> vars) {
		vars.put("isPad", isPad ? 1.0 : 0.0);
	}
	
	protected UUID getUUID() {
		if (stack == null || !(stack.getItem() instanceof ItemMinePad2))
			throw new RuntimeException("Get UUID is being called for a non-minepad UI");
		if (!stack.hasTag() || !stack.getTag().contains("PadID"))
			stack.getOrCreateTag().putUUID("PadID", UUID.randomUUID());
		
		return stack.getTag().getUUID("PadID");
	}
	
	@GuiSubscribe
	public void onButtonClicked(Button.ClickEvent ev) {
		if (ev.getSource() == btnCancel)
			minecraft.setScreen(null);
		else if (ev.getSource() == btnOk)
			validate(tfURL.getText());
		else if (ev.getSource() == btnShutDown) {
			if (isPad) {
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(
						getUUID(),
						""
				));
				stack.getTag().remove("PadID");
			}
			
			minecraft.setScreen(null);
		}
	}
	
	@GuiSubscribe
	public void onEnterPressed(TextField.EnterPressedEvent ev) {
		validate(ev.getText());
	}
	
	private void validate(String url) {
		if (!url.isEmpty()) {

			try {
				ScreenBlockEntity.url(url);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			url = Util.addProtocol(url);
//			url = ((ClientProxy) WebDisplays.PROXY).getMCEF().punycode(url);

			if (isPad) {
				UUID uuid = getUUID();
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(uuid, url));
				stack.getTag().putString("PadURL", url);

				ClientProxy.PadData pd = ((ClientProxy) WebDisplays.PROXY).getPadByID(uuid);

				if (pd != null && pd.view != null) {
					pd.view.loadURL(WebDisplays.applyBlacklist(url));
				}
			} else {
				// Only send URL if it changed
				if (!url.equals(screenURL)) {
					WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.setURL(tileEntity, screenSide, url, remoteLocation));
				}

				// Send volume settings for screens
				ScreenData screen = tileEntity.getScreen(screenSide);
				if (screen != null) {
					float volume = 100.0f;
					boolean autoVolume = true;

					try {
						volume = Float.parseFloat(tfVolume.getText());
						volume = Math.max(0, Math.min(100, volume));
					} catch (NumberFormatException e) {
						// Keep default value
					}

					autoVolume = cbAutoVolume.isChecked();

					// Only send volume if it changed
					if (volume != screen.volume || autoVolume != screen.autoVolume) {
						WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(
							tileEntity,
							screenSide,
							new VolumeControl(volume, autoVolume)
						));
					}
				}
			}
		}

		minecraft.setScreen(null);
	}
	
	@Override
	public boolean isForBlock(BlockPos bp, BlockSide side) {
		return (remoteLocation != null && remoteLocation.equalsBlockPos(bp)) || (bp.equals(tileEntity.getBlockPos()) && side == screenSide);
	}
	
}
