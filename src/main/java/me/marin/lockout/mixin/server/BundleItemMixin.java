package me.marin.lockout.mixin.server;

import me.marin.lockout.Lockout;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.misc.FillBundleWithBundlesGoal;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleItem.class)
public class BundleItemMixin {

    @Inject(method = "overrideStackedOnOther", at = @At("TAIL"))
    public void overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction clickAction, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide) return;
        if (!cir.getReturnValueZ()) return;
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        lockout$checkBundleFilled(lockout, player, stack);
    }

    @Inject(method = "overrideOtherStackedOnMe", at = @At("TAIL"))
    public void overrideOtherStackedOnMe(ItemStack stack, ItemStack otherStack, Slot slot, ClickAction clickAction, Player player, SlotAccess slotAccess, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide) return;
        if (!cir.getReturnValueZ()) return;
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        lockout$checkBundleFilled(lockout, player, stack);
    }

    @Unique
    private static void lockout$checkBundleFilled(Lockout lockout, Player player, ItemStack stack) {
        BundleContents bcc = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bcc == null) return;

        long bundleCount = bcc.itemCopyStream().filter(s -> s.getItem() instanceof BundleItem).count();
        if (bundleCount < 16) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;
            if (goal instanceof FillBundleWithBundlesGoal) {
                lockout.completeGoal(goal, player);
            }
        }
    }

}
