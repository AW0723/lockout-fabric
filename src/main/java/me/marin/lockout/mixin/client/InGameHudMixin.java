package me.marin.lockout.mixin.client;

import me.marin.lockout.Lockout;
import me.marin.lockout.Utility;
import me.marin.lockout.client.LockoutClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void renderBoard(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Lockout.exists(LockoutClient.lockout)) {
            return;
        }

        Utility.drawBingoBoard(context);
    }

}
