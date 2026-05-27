package net.montoyo.wd.client.gui;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import net.montoyo.wd.config.ClientConfig;
import net.montoyo.wd.config.CommonConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-game configuration screen (Mods menu) for WebDisplays.
 * Single-column, scrollable, English labels/descriptions.
 */
public class WebDisplaysConfigScreen extends Screen {

    private enum Page { CLIENT, COMMON }

    private Page page = Page.CLIENT;
    private final Screen parent;

    private static class Label {
        final String text;
        final int x;
        final int baseY;
        final int color;

        Label(String text, int x, int baseY, int color) {
            this.text = text;
            this.x = x;
            this.baseY = baseY;
            this.color = color;
        }
    }

    private static class WidgetPos {
        final AbstractWidget widget;
        final int baseY;

        WidgetPos(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }

    private static class PercentSlider extends AbstractSliderButton {
        private final String label;
        private final Consumer<Integer> setter;

        PercentSlider(int x, int y, int width, int height, String label, int value, Consumer<Integer> setter) {
            super(x, y, width, height, Component.empty(), value / 100.0);
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(value * 100.0);
            setMessage(Component.literal(label + ": " + percent + "%"));
        }

        @Override
        protected void applyValue() {
            int percent = (int) Math.round(value * 100.0);
            if (percent < 1)
                percent = 1;
            else if (percent > 100)
                percent = 100;
            setter.accept(percent);
        }

        public int getPercent() {
            return (int) Math.round(value * 100.0);
        }
    }

    private final List<Label> labels = new ArrayList<>();
    private final List<WidgetPos> widgets = new ArrayList<>();
    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private int contentHeight = 0;
    private double scrollOffset = 0;
    private int panelX = 0;
    private int panelY = 0;
    private int panelWidth = 0;
    private int panelHeight = 0;
    private int contentX = 0;
    private int contentWidth = 0;
    private int scrollTop = 0;
    private int scrollBottom = 0;

    // Client values
    private double loadDistance = ClientConfig.loadDistance;
    private double unloadDistance = ClientConfig.unloadDistance;
    private int padResolution = ClientConfig.padResolution;
    private int screenQuality = ClientConfig.screenQuality;
    private boolean sidePad = ClientConfig.sidePad;
    private boolean keyboardCamera = ClientConfig.Input.keyboardCamera;
    private boolean switchButtons = ClientConfig.Input.switchButtons;
    private boolean autoVolumeEnabled = ClientConfig.AutoVolumeControl.enableAutoVolume;
    private double autoVolumeDefault = ClientConfig.AutoVolumeControl.defaultVolume;
    private double autoVolumeMaxDistance = ClientConfig.AutoVolumeControl.maxDistance;
    private double autoVolumeMinDistance = ClientConfig.AutoVolumeControl.minDistance;
    private double screenOffsetDistance = ClientConfig.ScreenOffset.distance;
    private double screenOffsetPixels = ClientConfig.ScreenOffset.pixels;
    private boolean ambilightEnabled = ClientConfig.Ambilight.enabled;
    private int ambilightInterval = ClientConfig.Ambilight.intervalMs;
    private int ambilightSmooth = ClientConfig.Ambilight.smoothMs;
    private int ambilightSourcesPerEdge = ClientConfig.Ambilight.sourcesPerEdge;
    private double ambilightRadius = ClientConfig.Ambilight.radius;
    private double ambilightOffset = ClientConfig.Ambilight.offset;

    // Common values
    private boolean hardRecipes = CommonConfig.hardRecipes;
    private boolean joinMessage = CommonConfig.joinMessage;
    private boolean disableOwnershipThief = CommonConfig.disableOwnershipThief;
    private int miniservPort = CommonConfig.MiniServ.miniservPort;
    private long miniservQuota = CommonConfig.MiniServ.miniservQuota;
    private int maxResolutionX = CommonConfig.Screen.maxResolutionX;
    private int maxResolutionY = CommonConfig.Screen.maxResolutionY;
    private int maxScreenSizeX = CommonConfig.Screen.maxScreenSizeX;
    private int maxScreenSizeY = CommonConfig.Screen.maxScreenSizeY;
    private String blacklist = String.join(",", CommonConfig.Browser.blacklist);
    private String homepage = CommonConfig.Browser.homepage;

    // UI elements
    private EditBox loadDistanceField;
    private EditBox unloadDistanceField;
    private EditBox padResolutionField;
    private EditBox autoVolumeDefaultField;
    private EditBox autoVolumeMaxField;
    private EditBox autoVolumeMinField;
    private EditBox screenOffsetDistanceField;
    private EditBox screenOffsetPixelsField;
    private EditBox ambilightIntervalField;
    private EditBox ambilightSmoothField;
    private EditBox ambilightSourcesPerEdgeField;
    private EditBox ambilightRadiusField;
    private EditBox ambilightOffsetField;
    private PercentSlider screenQualitySlider;

    private EditBox miniservPortField;
    private EditBox miniservQuotaField;
    private EditBox maxResXField;
    private EditBox maxResYField;
    private EditBox maxScreenXField;
    private EditBox maxScreenYField;
    private EditBox blacklistField;
    private EditBox homepageField;

    private boolean sidePadValue = sidePad;
    private boolean keyboardCameraValue = keyboardCamera;
    private boolean switchButtonsValue = switchButtons;
    private boolean autoVolumeEnabledValue = autoVolumeEnabled;
    private boolean ambilightEnabledValue = ambilightEnabled;
    private boolean hardRecipesValue = hardRecipes;
    private boolean joinMessageValue = joinMessage;
    private boolean disableOwnershipThiefValue = disableOwnershipThief;

    private String statusMessage = "";
    private int statusColor = 0xA0A0A0;

    public WebDisplaysConfigScreen(Screen parent) {
        super(Component.literal("WebDisplays Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        labels.clear();
        widgets.clear();
        scrollableWidgets.clear();
        fixedWidgets.clear();

        int maxWidth = Math.max(240, this.width - 30);
        panelWidth = Math.min(440, maxWidth);
        panelX = (this.width - panelWidth) / 2;
        panelY = 45;
        int panelBottom = this.height - 70;
        panelHeight = Math.max(80, panelBottom - panelY);
        contentX = panelX + 12;
        contentWidth = panelWidth - 24;
        scrollTop = panelY + 6;
        scrollBottom = panelY + panelHeight - 6;
        int y = panelY + 12;
        int rowHeight = 78;

        addFixedWidget(Button.builder(Component.literal(page == Page.CLIENT ? "Page: Client" : "Page: Common"), b -> {
            if (captureCurrentPage()) {
                page = (page == Page.CLIENT) ? Page.COMMON : Page.CLIENT;
                init();
            }
        }).pos(this.width / 2 - 75, 20).size(150, 20).build());

        if (page == Page.CLIENT) {
            loadDistanceField = addNumberField(contentX, contentWidth, y, "Load distance (blocks)", "Screens start rendering within this distance.", loadDistance); y += rowHeight;
            unloadDistanceField = addNumberField(contentX, contentWidth, y, "Unload distance (blocks)", "Screens stop rendering beyond this distance.", unloadDistance); y += rowHeight;
            padResolutionField = addNumberField(contentX, contentWidth, y, "MinePad resolution", "Height in pixels (higher = sharper but heavier).", padResolution); y += rowHeight;
            screenQualitySlider = addPercentSlider(contentX, contentWidth, y, "Screen quality", "Scale screen resolution (100 = full, 50 = half).", screenQuality, val -> screenQuality = val); y += rowHeight;

            addLabeledToggle(contentX, contentWidth, y, "Side MinePad", "Show the MinePad at the edge of the screen.", sidePadValue, val -> sidePadValue = val); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Keyboard camera", "Focus camera on selected element while typing.", keyboardCameraValue, val -> keyboardCameraValue = val); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Swap laser buttons", "Invert left/right laser buttons.", switchButtonsValue, val -> switchButtonsValue = val); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Auto-volume", "Adjust volume based on distance.", autoVolumeEnabledValue, val -> autoVolumeEnabledValue = val); y += rowHeight;

            autoVolumeDefaultField = addNumberField(contentX, contentWidth, y, "Default volume (0-100)", "Initial screen volume.", autoVolumeDefault); y += rowHeight;
            autoVolumeMinField = addNumberField(contentX, contentWidth, y, "Full-volume distance (blocks)", "Max volume up to this distance.", autoVolumeMinDistance); y += rowHeight;
            autoVolumeMaxField = addNumberField(contentX, contentWidth, y, "Silent distance (blocks)", "Silent beyond this distance.", autoVolumeMaxDistance); y += rowHeight;
            screenOffsetDistanceField = addNumberField(contentX, contentWidth, y, "Screen offset distance (blocks)", "Distance after which the screen is nudged forward.", screenOffsetDistance); y += rowHeight;
            screenOffsetPixelsField = addNumberField(contentX, contentWidth, y, "Screen offset pixels", "Forward offset in pixels (1 pixel = 1/16 block).", screenOffsetPixels); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Ambilight", "Average screen color drives a colored light.", ambilightEnabledValue, val -> ambilightEnabledValue = val); y += rowHeight;
            ambilightIntervalField = addNumberField(contentX, contentWidth, y, "Ambilight interval (ms)", "Time between color samples.", ambilightInterval); y += rowHeight;
            ambilightSmoothField = addNumberField(contentX, contentWidth, y, "Ambilight smooth (ms)", "Transition time between colors (0 = instant).", ambilightSmooth); y += rowHeight;
            ambilightSourcesPerEdgeField = addNumberField(contentX, contentWidth, y, "Ambilight sources per edge", "1 = 1 light, 2 = 4, 3 = 8 (no center).", ambilightSourcesPerEdge); y += rowHeight;
            ambilightRadiusField = addNumberField(contentX, contentWidth, y, "Ambilight radius (multiplier)", "Screen size multiplied by this value.", ambilightRadius); y += rowHeight;
            ambilightOffsetField = addNumberField(contentX, contentWidth, y, "Ambilight forward offset (blocks)", "Distance in front of the screen.", ambilightOffset); y += rowHeight;
        } else {
            addLabeledToggle(contentX, contentWidth, y, "Hard recipes", "Break the MinePad to craft upgrades.", hardRecipesValue, val -> hardRecipesValue = val); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Join message", "Show a welcome message on login.", joinMessageValue, val -> joinMessageValue = val); y += rowHeight;
            addLabeledToggle(contentX, contentWidth, y, "Disable ownership thief", "Disable the ownership thief item.", disableOwnershipThiefValue, val -> disableOwnershipThiefValue = val); y += rowHeight;

            miniservPortField = addNumberField(contentX, contentWidth, y, "Miniserv port", "0 to disable.", miniservPort); y += rowHeight;
            miniservQuotaField = addNumberField(contentX, contentWidth, y, "Miniserv quota (KiB)", "Maximum upload size to miniserv.", miniservQuota); y += rowHeight;
            maxResXField = addNumberField(contentX, contentWidth, y, "Max resolution X", "Horizontal pixel cap for screens.", maxResolutionX); y += rowHeight;
            maxResYField = addNumberField(contentX, contentWidth, y, "Max resolution Y", "Vertical pixel cap for screens.", maxResolutionY); y += rowHeight;
            maxScreenXField = addNumberField(contentX, contentWidth, y, "Max screen width (blocks)", "Maximum horizontal blocks.", maxScreenSizeX); y += rowHeight;
            maxScreenYField = addNumberField(contentX, contentWidth, y, "Max screen height (blocks)", "Maximum vertical blocks.", maxScreenSizeY); y += rowHeight;

            blacklistField = addTextField(contentX, contentWidth, y, "Blacklist (comma-separated)", "Forbidden domains (ex: site1.com,site2.com).", blacklist); y += rowHeight;
            homepageField = addTextField(contentX, contentWidth, y, "Homepage URL", "Default page to load.", homepage); y += rowHeight;
        }

        contentHeight = y - (panelY + 12);
        applyScroll();

        addFixedWidget(Button.builder(Component.literal("Save"), b -> {
            if (captureCurrentPage() && saveConfigs()) {
                this.minecraft.setScreen(parent);
            }
        }).pos(this.width / 2 - 155, this.height - 35).size(150, 20).build());

        addFixedWidget(Button.builder(Component.literal("Cancel"), b -> this.minecraft.setScreen(parent))
                .pos(this.width / 2 + 5, this.height - 35).size(150, 20).build());
    }

    private EditBox addNumberField(int x, int width, int y, String label, String desc, double value) {
        labels.add(new Label(label, x, y - 10, 0xFFFFFF));
        labels.add(new Label(desc, x, y + 12, 0xA0A0A0));
        EditBox box = new EditBox(this.font, x, y + 24, width, 20, Component.literal(label));
        box.setValue(trimNumber(value));
        addScrollableWidget(box);
        widgets.add(new WidgetPos(box, y + 24));
        return box;
    }

    private EditBox addTextField(int x, int width, int y, String label, String desc, String value) {
        labels.add(new Label(label, x, y - 10, 0xFFFFFF));
        labels.add(new Label(desc, x, y + 12, 0xA0A0A0));
        EditBox box = new EditBox(this.font, x, y + 24, width, 20, Component.literal(label));
        box.setValue(value);
        addScrollableWidget(box);
        widgets.add(new WidgetPos(box, y + 24));
        return box;
    }

    private PercentSlider addPercentSlider(int x, int width, int y, String label, String desc, int value, Consumer<Integer> setter) {
        labels.add(new Label(label, x, y - 10, 0xFFFFFF));
        labels.add(new Label(desc, x, y + 12, 0xA0A0A0));
        PercentSlider slider = new PercentSlider(x, y + 24, Math.min(200, width), 20, label, value, setter);
        addScrollableWidget(slider);
        widgets.add(new WidgetPos(slider, y + 24));
        return slider;
    }

    private void addLabeledToggle(int x, int width, int y, String label, String desc, boolean initial, Consumer<Boolean> setter) {
        labels.add(new Label(label, x, y - 10, 0xFFFFFF));
        labels.add(new Label(desc, x, y + 12, 0xA0A0A0));
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(initial)
                .create(x, y + 24, Math.min(200, width), 20, Component.literal(label), (button, val) -> setter.accept(val));
        addScrollableWidget(btn);
        widgets.add(new WidgetPos(btn, y + 24));
    }

    private boolean captureCurrentPage() {
        if (page == Page.CLIENT) {
            return readClientFields();
        } else {
            return readCommonFields();
        }
    }

    private boolean readClientFields() {
        try {
            loadDistance = Double.parseDouble(loadDistanceField.getValue());
            unloadDistance = Double.parseDouble(unloadDistanceField.getValue());
            padResolution = (int) Double.parseDouble(padResolutionField.getValue());
            if (screenQualitySlider != null)
                screenQuality = screenQualitySlider.getPercent();
            autoVolumeDefault = Double.parseDouble(autoVolumeDefaultField.getValue());
            autoVolumeMinDistance = Double.parseDouble(autoVolumeMinField.getValue());
            autoVolumeMaxDistance = Double.parseDouble(autoVolumeMaxField.getValue());
            screenOffsetDistance = Double.parseDouble(screenOffsetDistanceField.getValue());
            screenOffsetPixels = Double.parseDouble(screenOffsetPixelsField.getValue());
            ambilightInterval = (int) Double.parseDouble(ambilightIntervalField.getValue());
            ambilightSmooth = (int) Double.parseDouble(ambilightSmoothField.getValue());
            ambilightSourcesPerEdge = (int) Double.parseDouble(ambilightSourcesPerEdgeField.getValue());
            ambilightRadius = Double.parseDouble(ambilightRadiusField.getValue());
            ambilightOffset = Double.parseDouble(ambilightOffsetField.getValue());
        } catch (NumberFormatException ex) {
            setStatus("Error: invalid numeric values", 0xFF5555);
            return false;
        }

        if (screenQuality < 1 || screenQuality > 100) {
            setStatus("Screen quality must be 1-100", 0xFF5555);
            return false;
        }

        if (autoVolumeDefault < 0 || autoVolumeDefault > 100) {
            setStatus("Default volume must be 0-100", 0xFF5555);
            return false;
        }

        if (autoVolumeMaxDistance < autoVolumeMinDistance) {
            setStatus("Silent distance must be >= full-volume distance", 0xFF5555);
            return false;
        }

        if (screenOffsetDistance < 0 || screenOffsetPixels < 0) {
            setStatus("Screen offset values must be >= 0", 0xFF5555);
            return false;
        }

        if (ambilightInterval < 1) {
            setStatus("Ambilight interval must be >= 1", 0xFF5555);
            return false;
        }

        if (ambilightSmooth < 0) {
            setStatus("Ambilight smooth must be >= 0", 0xFF5555);
            return false;
        }

        if (ambilightSourcesPerEdge < 1) {
            setStatus("Ambilight sources per edge must be >= 1", 0xFF5555);
            return false;
        }

        if (ambilightRadius < 0) {
            setStatus("Ambilight radius must be >= 0", 0xFF5555);
            return false;
        }

        if (ambilightOffset < 0) {
            setStatus("Ambilight offset must be >= 0", 0xFF5555);
            return false;
        }

        return true;
    }

    private boolean readCommonFields() {
        try {
            miniservPort = (int) Double.parseDouble(miniservPortField.getValue());
            miniservQuota = (long) Double.parseDouble(miniservQuotaField.getValue());
            maxResolutionX = (int) Double.parseDouble(maxResXField.getValue());
            maxResolutionY = (int) Double.parseDouble(maxResYField.getValue());
            maxScreenSizeX = (int) Double.parseDouble(maxScreenXField.getValue());
            maxScreenSizeY = (int) Double.parseDouble(maxScreenYField.getValue());
        } catch (NumberFormatException ex) {
            setStatus("Error: invalid numeric values", 0xFF5555);
            return false;
        }

        blacklist = blacklistField.getValue();
        homepage = homepageField.getValue();
        return true;
    }

    private boolean saveConfigs() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path clientPath = configDir.resolve("webdisplays_client.toml");
        Path commonPath = configDir.resolve("webdisplays_common.toml");

        try (CommentedFileConfig clientCfg = CommentedFileConfig.builder(clientPath).sync().preserveInsertionOrder().build()) {
            clientCfg.load();
            clientCfg.set("load_distance", loadDistance);
            clientCfg.set("unload_distance", unloadDistance);
            clientCfg.set("pad_resolution", padResolution);
            clientCfg.set("screen_quality", screenQuality);
            clientCfg.set("side_pad", sidePadValue);
            clientCfg.set("input.keyboard_camera", keyboardCameraValue);
            clientCfg.set("input.switch_buttons", switchButtonsValue);
            clientCfg.set("auto_volume.enabled", autoVolumeEnabledValue);
            clientCfg.set("auto_volume.default_volume", autoVolumeDefault);
            clientCfg.set("auto_volume.max_distance", autoVolumeMaxDistance);
            clientCfg.set("auto_volume.min_distance", autoVolumeMinDistance);
            clientCfg.set("screen_offset.distance", screenOffsetDistance);
            clientCfg.set("screen_offset.pixels", screenOffsetPixels);
            clientCfg.set("ambilight.enabled", ambilightEnabledValue);
            clientCfg.set("ambilight.interval_ms", ambilightInterval);
            clientCfg.set("ambilight.smooth_ms", ambilightSmooth);
            clientCfg.set("ambilight.sources_per_edge", ambilightSourcesPerEdge);
            clientCfg.set("ambilight.radius", ambilightRadius);
            clientCfg.set("ambilight.offset", ambilightOffset);
            clientCfg.save();
        } catch (Exception e) {
            setStatus("Failed to write webdisplays_client.toml: " + e.getMessage(), 0xFF5555);
            return false;
        }

        try (CommentedFileConfig commonCfg = CommentedFileConfig.builder(commonPath).sync().preserveInsertionOrder().build()) {
            commonCfg.load();
            commonCfg.set("hard_recipes", hardRecipesValue);
            commonCfg.set("join_message", joinMessageValue);
            commonCfg.set("disable_ownership_thief", disableOwnershipThiefValue);
            commonCfg.set("mini_server.miniserv_port", miniservPort);
            commonCfg.set("mini_server.miniserv_quota", miniservQuota);
            commonCfg.set("screen_options.max_resolution_x", maxResolutionX);
            commonCfg.set("screen_options.max_resolution_y", maxResolutionY);
            commonCfg.set("screen_options.max_width", maxScreenSizeX);
            commonCfg.set("screen_options.max_height", maxScreenSizeY);
            commonCfg.set("screen_options.keep_shape_on_change", true);
            commonCfg.set("browser_options.blacklist", blacklist);
            commonCfg.set("browser_options.home_page", homepage);
            commonCfg.save();
        } catch (Exception e) {
            setStatus("Failed to write webdisplays_common.toml: " + e.getMessage(), 0xFF5555);
            return false;
        }

        ClientConfig.loadDistance = loadDistance;
        ClientConfig.unloadDistance = unloadDistance;
        ClientConfig.padResolution = padResolution;
        ClientConfig.screenQuality = screenQuality;
        ClientConfig.sidePad = sidePadValue;
        ClientConfig.Input.keyboardCamera = keyboardCameraValue;
        ClientConfig.Input.switchButtons = switchButtonsValue;
        ClientConfig.AutoVolumeControl.enableAutoVolume = autoVolumeEnabledValue;
        ClientConfig.AutoVolumeControl.defaultVolume = autoVolumeDefault;
        ClientConfig.AutoVolumeControl.maxDistance = autoVolumeMaxDistance;
        ClientConfig.AutoVolumeControl.minDistance = autoVolumeMinDistance;
        ClientConfig.ScreenOffset.distance = screenOffsetDistance;
        ClientConfig.ScreenOffset.pixels = screenOffsetPixels;
        ClientConfig.Ambilight.enabled = ambilightEnabledValue;
        ClientConfig.Ambilight.intervalMs = ambilightInterval;
        ClientConfig.Ambilight.smoothMs = ambilightSmooth;
        ClientConfig.Ambilight.sourcesPerEdge = ambilightSourcesPerEdge;
        ClientConfig.Ambilight.radius = ambilightRadius;
        ClientConfig.Ambilight.offset = ambilightOffset;
        ClientConfig.postLoad();

        CommonConfig.hardRecipes = hardRecipesValue;
        CommonConfig.joinMessage = joinMessageValue;
        CommonConfig.disableOwnershipThief = disableOwnershipThiefValue;
        CommonConfig.MiniServ.miniservPort = miniservPort;
        CommonConfig.MiniServ.miniservQuota = miniservQuota;
        CommonConfig.Screen.maxResolutionX = maxResolutionX;
        CommonConfig.Screen.maxResolutionY = maxResolutionY;
        CommonConfig.Screen.maxScreenSizeX = maxScreenSizeX;
        CommonConfig.Screen.maxScreenSizeY = maxScreenSizeY;
        CommonConfig.Screen.keepShapeOnChange = true;
        CommonConfig.Browser.blacklist = blacklist.isEmpty() ? new String[0] : blacklist.split(",");
        CommonConfig.Browser.homepage = homepage;
        CommonConfig.postLoad();

        setStatus("Configuration saved.", 0x55FF55);
        return true;
    }

    private String trimNumber(double value) {
        String str = Double.toString(value);
        if (str.endsWith(".0")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        int panelRight = panelX + panelWidth;
        int panelBottom = panelY + panelHeight;
        gfx.fill(panelX, panelY, panelRight, panelBottom, 0x66000000);
        gfx.fill(panelX, panelY, panelRight, panelY + 1, 0xFF808080);
        gfx.fill(panelX, panelBottom - 1, panelRight, panelBottom, 0xFF808080);
        gfx.fill(panelX, panelY, panelX + 1, panelBottom, 0xFF808080);
        gfx.fill(panelRight - 1, panelY, panelRight, panelBottom, 0xFF808080);

        gfx.enableScissor(panelX + 4, scrollTop, panelRight - 4, scrollBottom);

        for (Label label : labels) {
            int y = (int) (label.baseY - scrollOffset);
            if (y > scrollTop - 20 && y < scrollBottom) {
                gfx.drawString(this.font, label.text, label.x, y, label.color);
            }
        }
        for (AbstractWidget widget : scrollableWidgets) {
            if (widget.visible) {
                widget.render(gfx, mouseX, mouseY, partialTick);
            }
        }
        gfx.disableScissor();

        for (AbstractWidget widget : fixedWidgets) {
            widget.render(gfx, mouseX, mouseY, partialTick);
        }

        // Draw a simple scroll bar if content overflows
        int visibleHeight = scrollBottom - scrollTop;
        if (contentHeight > visibleHeight) {
            int barHeight = Math.max(20, (int) ((visibleHeight / (double) contentHeight) * (scrollBottom - scrollTop)));
            double maxScroll = contentHeight - visibleHeight;
            int barY = scrollTop + (int) ((scrollOffset / maxScroll) * ((scrollBottom - barHeight) - scrollTop));
            int barX = panelRight - 10;
            gfx.fill(barX, barY, barX + 6, barY + barHeight, 0x80FFFFFF);
        }

        if (!statusMessage.isEmpty()) {
            gfx.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 55, statusColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleHeight = scrollBottom - scrollTop;
        if (contentHeight > visibleHeight && isMouseOverPanel(mouseX, mouseY)) {
            double maxScroll = Math.max(0, contentHeight - visibleHeight);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 20));
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void applyScroll() {
        int visibleHeight = scrollBottom - scrollTop;
        double maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        } else if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        for (WidgetPos wp : widgets) {
            int widgetY = (int) (wp.baseY - scrollOffset);
            wp.widget.setY(widgetY);
            wp.widget.visible = widgetY + wp.widget.getHeight() > scrollTop && widgetY < scrollBottom;
        }
    }

    private boolean isMouseOverPanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    private <T extends AbstractWidget> T addScrollableWidget(T widget) {
        addRenderableWidget(widget);
        scrollableWidgets.add(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addFixedWidget(T widget) {
        addRenderableWidget(widget);
        fixedWidgets.add(widget);
        return widget;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
