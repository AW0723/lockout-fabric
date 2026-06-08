package me.marin.lockout.mixin.client;

import me.marin.lockout.LockoutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    public void extractRenderState(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (!LockoutConfig.getInstance().showNoiseRouterLine) {
            return;
        }
    }

}
