package me.marin.lockout.mixin.server;

import me.marin.lockout.Lockout;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.workstation.UseLoomGoal;
import me.marin.lockout.lockout.goals.workstation.UseStonecutterGoal;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method="onTake", at = @At("HEAD"))
    public void onTakeItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof UseStonecutterGoal) {
                if (player.containerMenu instanceof StonecutterMenu stonecutterMenu) {
                    if ((Object) this == stonecutterMenu.slots.get(1)) {
                        lockout.completeGoal(goal, player);
                    }
                }
            }

            if (goal instanceof UseLoomGoal) {
                if (player.containerMenu instanceof LoomMenu loomMenu) {
                    if ((Object) this == loomMenu.slots.get(loomMenu.slots.size() - 1)) {
                        lockout.completeGoal(goal, player);
                    }
                }
            }

        }
    }

}
