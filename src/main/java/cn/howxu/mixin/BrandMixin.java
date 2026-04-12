package cn.howxu.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidth;

import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

@Mixin(value = TitleScreen.class, priority = 1100)
public abstract class BrandMixin extends Screen {

    protected BrandMixin(MinecraftClient minecraftClient, TextRenderer textRenderer, Text text) {
        super(minecraftClient, textRenderer, text);
    }

    @Inject(method = "render", at = @At("TAIL"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onRender(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci, float f) {
        int alpha = MathHelper.ceil(f * 255.0F);
        String brand_info = "Performance enhanced for " + SharedConstants.getGameVersion().name() + " by howxu";
        context.drawTextWithShadow(this.textRenderer, brand_info, 2, this.height - 20, ColorHelper.getArgb(alpha,0,255,0));
    }
}
