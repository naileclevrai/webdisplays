/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.client.ClientProxy;
import net.montoyo.wd.client.gui.controls.Button;
import net.montoyo.wd.client.gui.controls.CheckBox;
import net.montoyo.wd.client.gui.controls.Label;
import net.montoyo.wd.client.gui.controls.TextField;
import net.montoyo.wd.client.gui.loading.FillControl;
import net.montoyo.wd.controls.builtin.ScreenModifyControl;
import net.montoyo.wd.controls.builtin.ScreenLinkControl;
import net.montoyo.wd.controls.builtin.ScreenRefreshControl;
import net.montoyo.wd.controls.builtin.TestPatternControl;
import net.montoyo.wd.controls.builtin.VolumeControl;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.entity.ScreenData;
import net.montoyo.wd.item.ItemMinePad2;
import net.montoyo.wd.net.WDNetworkRegistry;
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl;
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl;
import net.montoyo.wd.utilities.data.BlockSide;
import net.montoyo.wd.utilities.data.ScreenLinkMode;
import net.montoyo.wd.utilities.data.ScreenShapeMode;
import net.montoyo.wd.utilities.link.LinkedScreenHelper;
import net.montoyo.wd.utilities.serialization.Util;
import net.montoyo.wd.utilities.math.Vector3i;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class GuiSetURL2 extends WDScreen {
	
	private ScreenBlockEntity tileEntity;
	private BlockSide screenSide;
	private Vector3i remoteLocation;
	
	private ItemStack stack;
	private final boolean isPad;
	
	private final String screenURL;
	private ScreenShapeMode shapeMode = ScreenShapeMode.NONE;
	private ScreenLinkMode linkMode = ScreenLinkMode.JOINED;
	private boolean linkDesired = false;
	
	@FillControl
	private TextField tfURL;

	@FillControl
	private TextField tfVolume;

	@FillControl
	private CheckBox cbAutoVolume;

	@FillControl
	private Button btnShapeMode;

	@FillControl
	private Label lblLinkSection;

	@FillControl
	private Label lblLinkRole;

	@FillControl
	private Label lblLinkHelp;

	@FillControl
	private TextField tfLinkId;

	@FillControl
	private Button btnLinkAuto;

	@FillControl
	private Button btnLink;

	@FillControl
	private Button btnLinkMode;

	@FillControl
	private Button btnRefreshBrowser;

	@FillControl
	private Button btnTestPattern;

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

		if (!isPad && tileEntity != null) {
			ScreenData screen = tileEntity.getScreen(screenSide);
			if (screen != null) {
				shapeMode = screen.shapeMode;
				linkMode = screen.linkMode == null ? ScreenLinkMode.JOINED : screen.linkMode;
				linkDesired = screen.isLinked();
				if (tfLinkId != null)
					tfLinkId.setText(screen.linkId == null ? "" : screen.linkId);
				updateShapeModeStr();
				updateLinkLabels();
				updateTestPatternButton(screen);
				refreshLinkedUi(screen);
			}
		}
	}

	private void refreshLinkedUi(ScreenData screen) {
		if (screen == null)
			return;

		boolean isSlave = LinkedScreenHelper.isLinkedSlave(screen);
		ScreenData settings = screen;
		if (WebDisplays.PROXY instanceof ClientProxy proxy)
			settings = LinkedScreenHelper.getOriginScreen(proxy, tileEntity, screen);
		if (settings == null)
			settings = screen;

		tfURL.setText(settings.url != null ? settings.url : screenURL);
		tfURL.setDisabled(isSlave);

		tfVolume.setText(String.valueOf((int) settings.volume));
		tfVolume.setDisabled(isSlave);
		cbAutoVolume.setChecked(settings.autoVolume);
		cbAutoVolume.setDisabled(isSlave);

		if (lblLinkRole != null) {
			if (LinkedScreenHelper.isDiagonalAutoLink(screen))
				lblLinkRole.setLabel(I18n.get("webdisplays.gui.seturl.linkrole.diagonal"));
			else if (!screen.isLinked())
				lblLinkRole.setLabel(I18n.get("webdisplays.gui.seturl.linkrole.standalone"));
			else if (screen.linkOrigin)
				lblLinkRole.setLabel(I18n.get("webdisplays.gui.seturl.linkrole.origin"));
			else
				lblLinkRole.setLabel(I18n.get("webdisplays.gui.seturl.linkrole.slave"));
			lblLinkRole.setColor(isSlave ? 0xFFAAAAAA : (screen.linkOrigin ? 0xFF55FF55 : 0xFFFFFFFF));
		}

		if (lblLinkHelp != null) {
			if (LinkedScreenHelper.isDiagonalAutoLink(screen))
				lblLinkHelp.setLabel(I18n.get("webdisplays.gui.seturl.linkhelp.diagonal"));
			else if (isSlave)
				lblLinkHelp.setLabel(I18n.get("webdisplays.gui.seturl.linkhelp.slave"));
			else if (screen.isLinked() && screen.linkOrigin)
				lblLinkHelp.setLabel(I18n.get("webdisplays.gui.seturl.linkhelp.origin"));
			else
				lblLinkHelp.setLabel(I18n.get("webdisplays.gui.seturl.linkhelp.standalone"));
		}

		boolean autoDiagonal = LinkedScreenHelper.isDiagonalAutoLink(screen);
		if (btnLinkAuto != null)
			btnLinkAuto.setDisabled(isSlave || autoDiagonal);
		if (btnLink != null)
			btnLink.setDisabled(isSlave || autoDiagonal);
		if (btnLinkMode != null)
			btnLinkMode.setDisabled(isSlave || autoDiagonal);
		if (tfLinkId != null) {
			tfLinkId.setDisabled(isSlave || autoDiagonal);
			if (autoDiagonal)
				tfLinkId.setText("");
		}
	}

	private void updateTestPatternButton(ScreenData screen) {
		if (btnTestPattern == null || screen == null)
			return;
		boolean canToggle = !LinkedScreenHelper.isLinkedSlave(screen);
		btnTestPattern.setDisabled(!canToggle);
		String key = screen.testPattern
				? "webdisplays.gui.seturl.testpattern.stop"
				: "webdisplays.gui.seturl.testpattern.start";
		btnTestPattern.setLabel(I18n.get(key));
	}

	private void updateShapeModeStr() {
		if (btnShapeMode != null)
			btnShapeMode.setLabel(I18n.get("webdisplays.gui.screencfg.shapemode." + shapeMode.name().toLowerCase()));
	}

	private void updateLinkLabels() {
		if (btnLink != null) {
			String key = linkDesired ? "webdisplays.gui.seturl.link.unlink" : "webdisplays.gui.seturl.link.sync";
			btnLink.setLabel(I18n.get(key));
		}
		if (btnLinkMode != null) {
			String key = "webdisplays.gui.seturl.linkmode." + linkMode.name().toLowerCase();
			btnLinkMode.setLabel(I18n.get(key));
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
		else if (ev.getSource() == btnShapeMode) {
			ScreenShapeMode[] modes = ScreenShapeMode.values();
			shapeMode = modes[(shapeMode.ordinal() + 1) % modes.length];
			updateShapeModeStr();
			if (tileEntity != null)
				tileEntity.setShapeMode(screenSide, shapeMode);
			WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(tileEntity, screenSide, new ScreenModifyControl(shapeMode)));
		}
		else if (ev.getSource() == btnLinkAuto) {
			if (!isPad && tfLinkId != null) {
				String generated = "wd-" + UUID.randomUUID().toString().substring(0, 8);
				tfLinkId.setText(generated);
				linkDesired = true;
				updateLinkLabels();
			}
		}
		else if (ev.getSource() == btnLink) {
			if (!isPad && tfLinkId != null) {
				if (linkDesired) {
					linkDesired = false;
				} else {
					String id = tfLinkId.getText().trim();
					if (!id.isEmpty())
						linkDesired = true;
				}
				updateLinkLabels();
			}
		}
		else if (ev.getSource() == btnLinkMode) {
			ScreenLinkMode[] modes = ScreenLinkMode.values();
			linkMode = modes[(linkMode.ordinal() + 1) % modes.length];
			updateLinkLabels();
		}
		else if (ev.getSource() == btnRefreshBrowser) {
			if (!isPad && tileEntity != null)
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(
					tileEntity,
					screenSide,
					new ScreenRefreshControl()
				));
		}
		else if (ev.getSource() == btnTestPattern) {
			if (!isPad && tileEntity != null) {
				ScreenData screen = tileEntity.getScreen(screenSide);
				if (screen != null && !LinkedScreenHelper.isLinkedSlave(screen)) {
					boolean next = !screen.testPattern;
					tileEntity.setTestPattern(screenSide, next);
					WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(
						tileEntity,
						screenSide,
						new TestPatternControl(next)
					));
					updateTestPatternButton(tileEntity.getScreen(screenSide));
				}
			}
		}
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

			if (isPad) {
				UUID uuid = getUUID();
				WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(uuid, url));
				stack.getTag().putString("PadURL", url);

				ClientProxy.PadData pd = ((ClientProxy) WebDisplays.PROXY).getPadByID(uuid);

				if (pd != null && pd.view != null) {
					pd.view.loadURL(WebDisplays.applyBlacklist(url));
				}
			} else {
				ScreenData screen = tileEntity.getScreen(screenSide);
				if (screen == null) {
					minecraft.setScreen(null);
					return;
				}

				boolean isSlave = LinkedScreenHelper.isLinkedSlave(screen);
				boolean canChangeUrl = !isSlave && (!screen.isLinked() || screen.linkOrigin);
				if (canChangeUrl && !url.equals(screenURL)) {
					WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.setURL(tileEntity, screenSide, url, remoteLocation));
				}

				if (!isSlave) {
					float volume = 100.0f;
					boolean autoVolume = true;

					try {
						volume = Float.parseFloat(tfVolume.getText());
						volume = Math.max(0, Math.min(100, volume));
					} catch (NumberFormatException e) {
						// Keep default value
					}

					autoVolume = cbAutoVolume.isChecked();

					if (volume != screen.volume || autoVolume != screen.autoVolume) {
						WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(
							tileEntity,
							screenSide,
							new VolumeControl(volume, autoVolume)
						));
					}

					if (!LinkedScreenHelper.isDiagonalAutoLink(screen)) {
					String newId = tfLinkId != null ? tfLinkId.getText().trim() : "";
					boolean shouldLink = linkDesired && !newId.isEmpty();
					boolean hasOther = hasLinkedScreen(newId);
					boolean keepOrigin = screen.linkOrigin && newId.equals(screen.linkId);
					boolean newOrigin = shouldLink && (keepOrigin || !hasOther);

					if (!shouldLink) {
						newId = "";
						newOrigin = false;
					}

					if (!newId.equals(screen.linkId) || screen.linkMode != linkMode || screen.linkOrigin != newOrigin) {
						WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageScreenCtrl(
							tileEntity,
							screenSide,
							new ScreenLinkControl(newId, linkMode, newOrigin)
						));
					}
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

	private boolean hasLinkedScreen(String linkId) {
		if (linkId == null || linkId.isEmpty())
			return false;
		if (!(WebDisplays.PROXY instanceof ClientProxy proxy))
			return false;
		for (ScreenBlockEntity te : proxy.getScreens()) {
			for (int i = 0; i < te.screenCount(); i++) {
				ScreenData scr = te.getScreen(i);
				if (scr == null || !scr.isLinked())
					continue;
				if (!linkId.equals(scr.linkId))
					continue;
				if (te == tileEntity && scr.side == screenSide)
					continue;
				return true;
			}
		}
		return false;
	}
	
}
