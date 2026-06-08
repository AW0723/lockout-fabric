package me.marin.lockout.mixin.server;

import me.marin.lockout.Lockout;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.have_more.HaveMostUniqueCraftsGoal;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Mixin(ResultSlot.class)
public class CraftingResultSlotMixin {

    @Shadow @Final
    private Player player;

    @Shadow
    private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    public void onCraft(ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;

        if (removeCount < 0 || stack.isEmpty()) {
            return;
        }

        if (!(player.containerMenu instanceof CraftingMenu || player.containerMenu instanceof InventoryMenu)) return;

        lockout.uniqueCrafts.putIfAbsent(player.getUUID(), new HashSet<>());
        Set<Item> crafts = lockout.uniqueCrafts.get(player.getUUID());
        boolean addedNew = crafts.add(stack.getItem());

        if (!addedNew) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;

            if (goal instanceof HaveMostUniqueCraftsGoal) {
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                if (crafts.size() % 5 == 0) {
                    player.sendSystemMessage(Component.literal(ChatFormatting.GRAY + "" + ChatFormatting.ITALIC + "You have crafted " + crafts.size() + " unique items."));
                }
                player.sendOverlayMessage(Component.literal("Unique crafts: " + crafts.size()));

                if (crafts.size() > lockout.mostUniqueCrafts) {
                    if (!Objects.equals(lockout.mostUniqueCraftsPlayer, player.getUUID())) {
                        lockout.updateGoalCompletion(goal, player.getUUID());
                    }

                    lockout.mostUniqueCraftsPlayer = player.getUUID();
                    lockout.mostUniqueCrafts = crafts.size();
                }
            }
        }
    }

}
