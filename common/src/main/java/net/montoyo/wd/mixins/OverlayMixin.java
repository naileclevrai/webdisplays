package net.montoyo.wd.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.montoyo.wd.client.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

@Mixin(Gui.class)
public class OverlayMixin {
    @Inject(at = @At("HEAD"), method = "renderCrosshair", cancellable = true)
    public void preDrawCrosshair(GuiGraphics pGuiGraphics, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        ClientProxy.renderCrosshair(mc.options, screenWidth, screenHeight, 0, pGuiGraphics, ci);
    }
}
