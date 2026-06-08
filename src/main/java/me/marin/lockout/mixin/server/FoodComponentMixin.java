package me.marin.lockout.mixin.server;

import me.marin.lockout.Lockout;
import me.marin.lockout.LockoutTeamServer;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.opponent.OpponentEatsFoodGoal;
import me.marin.lockout.lockout.interfaces.ConsumeItemGoal;
import me.marin.lockout.lockout.interfaces.EatUniqueFoodsGoal;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;

@Mixin(FoodProperties.class)
public class FoodComponentMixin {

    @Inject(method = "onConsume", at = @At("HEAD"))
    public void onConsume(Level world, LivingEntity user, ItemStack itemStack, Consumable consumable, CallbackInfo ci) {

        if (world.isClientSide) return;

        if (!(user instanceof Player player)) return;

        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;

        if (!lockout.isLockoutPlayer(player.getUUID())) return;
        LockoutTeamServer team = (LockoutTeamServer) lockout.getPlayerTeam(player.getUUID());


        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof ConsumeItemGoal consumeItemGoal) {
                if (consumeItemGoal.getItem().equals(itemStack.getItem())) {
                    lockout.completeGoal(goal, player);
                }
            }
            if (goal instanceof EatUniqueFoodsGoal eatUniqueFoodsGoal) {
                FoodProperties foodComponent = itemStack.get(DataComponents.FOOD);
                if (foodComponent != null) {
                    eatUniqueFoodsGoal.getTrackerMap().putIfAbsent(team, new LinkedHashSet<>());
                    eatUniqueFoodsGoal.getTrackerMap().get(team).add(itemStack.getItem());

                    int size = eatUniqueFoodsGoal.getTrackerMap().get(team).size();

                    team.sendTooltipUpdate(eatUniqueFoodsGoal);
                    if (size >= eatUniqueFoodsGoal.getAmount()) {
                        lockout.completeGoal(goal, team);
                    }
                }
            }
            if (goal instanceof OpponentEatsFoodGoal) {
                lockout.complete1v1Goal(goal, player, false, player.getName().getString() + " ate food.");
            }
        }

    }
}
