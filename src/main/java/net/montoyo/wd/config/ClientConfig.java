package net.montoyo.wd.config;

import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.montoyo.wd.WebDisplays;
import net.montoyo.wd.config.annoconfg.AnnoCFG;
import net.montoyo.wd.config.annoconfg.annotation.format.*;
import net.montoyo.wd.config.annoconfg.annotation.value.Default;
import net.montoyo.wd.config.annoconfg.annotation.value.DoubleRange;
import net.montoyo.wd.config.annoconfg.annotation.value.IntRange;

@Config(type = ModConfig.Type.CLIENT)
public class ClientConfig {
	@SuppressWarnings("unused")
	private static final AnnoCFG CFG = new AnnoCFG(FMLJavaModLoadingContext.get().getModEventBus(), ClientConfig.class);
	public static void init() {
		// loads the class
	}
	
	@Name("load_distance")
	@Comment("How far (in blocks) you can be before a screen starts rendering")
	@Translation("config.webdisplays.load_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 64)
	public static double loadDistance = 64.0;

	@Name("unload_distance")
	@Comment("How far you can be before a screen stops rendering")
	@Translation("config.webdisplays.unload_distance")
	@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
	@Default(valueD = 96)
	public static double unloadDistance = 96.0;
	
	@Name("pad_resolution")
	@Comment({
			"The resolution that minePads should use",
			"Smaller values produce lower qualities, higher values produce higher qualities",
			"Due to how web browsers work however, the larger this value is, the smaller text is",
			"Also, higher values will invariably lag more",
			"A good goto value for this would be the height of your monitor, in pixels",
			"A standard monitor is (at least currently) 1080",
	})
	@Translation("config.webdisplays.pad_res")
	@IntRange(minV = 0, maxV = Integer.MAX_VALUE)
	@Default(valueI = 720)
	public static int padResolution = 720;
	
	@Name("side_pad")
	@Comment({
			"When this is true, the minePad is placed off to the side of the screen when held, so it's visible but doesn't take up too much of the screen",
			"When this is false, the minePad is placed closer to the center of the screen, allow it to be seen better, but taking up more of your view",
	})
	@Translation("config.webdisplays.side_pad")
	@Default(valueBoolean = true)
	public static boolean sidePad = true;

	@Comment({
			"Options relating to input handling"
	})
	@CFGSegment("input")
	public static class Input {
		@Name("keyboard_camera")
		@Comment({
				"If this is on, then the camera will try to focus on the selected element while a keyboard is in use",
				"Elsewise, it'll try to focus on the center of the screen",
		})
		@Translation("config.webdisplays.keyboard_camera")
		@Default(valueBoolean = true)
		public static boolean keyboardCamera = true;

		@Name("switch_buttons")
		@Comment("If the left and right buttons should be swapped when using a laser")
		@Translation("config.webdisplays.switch_buttons")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 30)
		public static boolean switchButtons = true;
	}

	@Comment({
			"AutoVolume makes audio fade off based on distance"
	})
	@CFGSegment("auto_volume")
	public static class AutoVolumeControl {
		@Name("enabled")
		@Comment("Whether or not auto volume should be enabled by default for new screens")
		@Translation("config.webdisplays.auto_vol")
		@Default(valueBoolean = true)
		public static boolean enableAutoVolume = true;

		@Name("default_volume")
		@Comment("Default volume for screens (0-100)")
		@Translation("config.webdisplays.default_vol")
		@DoubleRange(minV = 0, maxV = 100)
		@Default(valueD = 100)
		public static double defaultVolume = 100.0;

		@Name("max_distance")
		@Comment("Distance after which you can't hear anything (in blocks)")
		@Translation("config.webdisplays.max_distance")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 30)
		public static double maxDistance = 30.0;

		@Name("min_distance")
		@Comment("Distance before which the sound is at full volume (in blocks)")
		@Translation("config.webdisplays.min_distance")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 10)
		public static double minDistance = 10.0;
	}

	@Comment({
			"Screen render offset to reduce distant z-fighting/artefacts"
	})
	@CFGSegment("screen_offset")
	public static class ScreenOffset {
		@Name("distance")
		@Comment("Distance (in blocks) beyond which the screen texture is nudged forward")
		@Translation("config.webdisplays.screen_offset.distance")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 64)
		public static double distance = 64.0;

		@Name("pixels")
		@Comment("Forward offset in pixels (1 pixel = 1/16 block). 0 disables the offset")
		@Translation("config.webdisplays.screen_offset.pixels")
		@DoubleRange(minV = 0, maxV = 64)
		@Default(valueD = 2)
		public static double pixels = 2.0;
	}

	@Comment({
			"Ambient light extracted from screen color"
	})
	@CFGSegment("ambilight")
	public static class Ambilight {
		@Name("enabled")
		@Comment("Enable ambilight based on the average screen color")
		@Translation("config.webdisplays.ambilight.enabled")
		@Default(valueBoolean = false)
		public static boolean enabled = false;

		@Name("interval_ms")
		@Comment("Sampling interval in milliseconds")
		@Translation("config.webdisplays.ambilight.interval_ms")
		@IntRange(minV = 1, maxV = Integer.MAX_VALUE)
		@Default(valueI = 50)
		public static int intervalMs = 50;

		@Name("sources_per_edge")
		@Comment("Number of light samples per screen edge (1 = 1, 2 = 4, 3 = 8)")
		@Translation("config.webdisplays.ambilight.sources_per_edge")
		@IntRange(minV = 1, maxV = Integer.MAX_VALUE)
		@Default(valueI = 1)
		public static int sourcesPerEdge = 1;

		@Name("radius")
		@Comment("Light radius in blocks")
		@Translation("config.webdisplays.ambilight.radius")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 6)
		public static double radius = 6.0;

		@Name("offset")
		@Comment("Forward offset from the screen surface in blocks")
		@Translation("config.webdisplays.ambilight.offset")
		@DoubleRange(minV = 0, maxV = Double.MAX_VALUE)
		@Default(valueD = 0.52)
		public static double offset = 0.52;
	}

	@SuppressWarnings("unused")
	public static void postLoad() {
		if (unloadDistance < loadDistance + 2.0)
			unloadDistance = loadDistance + 2.0;

		if (AutoVolumeControl.maxDistance < AutoVolumeControl.minDistance + 0.1)
			AutoVolumeControl.maxDistance = AutoVolumeControl.minDistance + 0.1;

		if (ScreenOffset.distance < 0)
			ScreenOffset.distance = 0;
		if (ScreenOffset.pixels < 0)
			ScreenOffset.pixels = 0;
		if (Ambilight.intervalMs < 1)
			Ambilight.intervalMs = 1;
		if (Ambilight.sourcesPerEdge < 1)
			Ambilight.sourcesPerEdge = 1;
		if (Ambilight.radius < 0)
			Ambilight.radius = 0;
		if (Ambilight.offset < 0)
			Ambilight.offset = 0;

		// cache pad resolution
		WebDisplays.INSTANCE.padResY = padResolution;
		WebDisplays.INSTANCE.padResX = WebDisplays.INSTANCE.padResY * WebDisplays.PAD_RATIO;

		// cache unload/load distances
		WebDisplays.INSTANCE.unloadDistance2 = unloadDistance * unloadDistance;
		WebDisplays.INSTANCE.loadDistance2 = loadDistance * loadDistance;

		// cache audio distances
		WebDisplays.INSTANCE.avDist100 = (float) AutoVolumeControl.minDistance;
		WebDisplays.INSTANCE.avDist0 = (float) AutoVolumeControl.maxDistance;

		// Debug: Print configuration values
		System.out.println("[WebDisplays] Configuration loaded:");
		System.out.println("[WebDisplays]   loadDistance = " + loadDistance + " blocks");
		System.out.println("[WebDisplays]   unloadDistance = " + unloadDistance + " blocks");
		System.out.println("[WebDisplays]   Effective render distance = " + Math.sqrt(loadDistance * loadDistance * 16) + " blocks");
	}
}
