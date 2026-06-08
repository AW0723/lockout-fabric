package me.marin.lockout.mixin.server;

import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raid.class)
public class RaidMixin {

    @Inject(method = "getNumGroups", at = @At("HEAD"), cancellable = true)
    public void getNumGroups(Difficulty difficulty, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(5);
    }

}
