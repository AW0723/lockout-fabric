package me.marin.lockout.mixin.server;

import me.marin.lockout.Lockout;
import me.marin.lockout.LockoutTeamServer;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.misc.HaveShieldDisabledGoal;
import me.marin.lockout.lockout.goals.misc.Sprint1KmGoal;
import me.marin.lockout.lockout.goals.misc.Take200DamageGoal;
import me.marin.lockout.lockout.goals.opponent.*;
import me.marin.lockout.lockout.interfaces.IncrementStatGoal;
import me.marin.lockout.lockout.interfaces.ReachXPLevelGoal;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "touch", at = @At("HEAD"))
    public void onCollide(Entity entity, CallbackInfo ci) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;

        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof OpponentHitBySnowballGoal) {
                if (entity instanceof Snowball snowballEntity) {
                    if (snowballEntity.getOwner() instanceof Player shooter && !Objects.equals(player, shooter)) {
                        lockout.complete1v1Goal(goal, shooter, true, shooter.getName().getString() + " hit " + player.getName().getString() + " with a Snowball.");
                    }
                }
            }
            if (goal instanceof OpponentHitByEggGoal) {
                if (entity instanceof ThrownEgg snowballEntity) {
                    if (snowballEntity.getOwner() instanceof Player shooter && !Objects.equals(player, shooter)) {
                        lockout.complete1v1Goal(goal, shooter, true, shooter.getName().getString() + " hit " + player.getName().getString() + " with an Egg.");
                    }
                }
            }
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    public void onStartMatch(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        if (world.isClientSide) return;
        if (!lockout.hasStarted()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    public void onDamage(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        if (!cir.getReturnValue()) return;

        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        if (!lockout.isLockoutPlayer(player.getUUID())) return;
        LockoutTeamServer team = (LockoutTeamServer) lockout.getPlayerTeam(player.getUUID());

        lockout.damageTaken.putIfAbsent(team, 0d);
        lockout.damageTaken.merge(team, (double)amount, Double::sum);

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof Take200DamageGoal take200DamageGoal) {
                team.sendTooltipUpdate(take200DamageGoal);
                if (lockout.damageTaken.get(team) >= 200) {
                    lockout.completeGoal(goal, team);
                }
            }
            if (goal instanceof OpponentTakesFallDamageGoal) {
                if (source.is(DamageTypes.FALL)) {
                    lockout.complete1v1Goal(goal, player, false, player.getName().getString() + " took fall damage.");
                }
            }
            if (goal instanceof OpponentTakes100DamageGoal) {
                if (lockout.damageTaken.get(team) >= 100) {
                    lockout.complete1v1Goal(goal, team, false, team.getDisplayName() + " took 100 damage.");
                }
            }
        }
    }

    @Inject(method = "awardStat(Lnet/minecraft/resources/Identifier;)V", at = @At("HEAD"))
    public void onIncrementStat(Identifier stat, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof IncrementStatGoal incrementStatGoal && incrementStatGoal.getStats().contains(stat)) {
                lockout.completeGoal(goal, player);
            }
            if (goal instanceof OpponentEatsFoodGoal && stat.equals(Stats.EAT_CAKE_SLICE)) {
                lockout.complete1v1Goal(goal, player, false, player.getName().getString() + " ate food.");
            }
        }
    }

    @Inject(method = "awardStat(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"))
    public void onIncreaseStat(Identifier stat, int amount, CallbackInfo ci) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;
            if (goal instanceof Sprint1KmGoal && stat.equals(Stats.SPRINT_ONE_CM)) {
                lockout.distanceSprinted.putIfAbsent(player.getUUID(), 0);
                lockout.distanceSprinted.merge(player.getUUID(), amount, Integer::sum);

                if (lockout.distanceSprinted.get(player.getUUID()) >= (100 * 1000)) {
                    lockout.completeGoal(goal, player);
                }
            }
        }
    }

    @Inject(method = "giveExperienceLevels", at = @At("TAIL"))
    public void onExperienceLevelUp(int levels, CallbackInfo ci) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof ReachXPLevelGoal reachXPLevelGoal) {
                if (player.experienceLevel >= reachXPLevelGoal.getAmount()) {
                    lockout.completeGoal(goal, player);
                }
            }
        }
    }

    @Inject(method = "blockUsingItem", at = @At(value = "TAIL"))
    public void onTakeShieldHit(ServerLevel world, LivingEntity attacker, CallbackInfo ci) {
        Lockout lockout = LockoutServer.lockout;
        if (!Lockout.isLockoutRunning(lockout)) return;
        Player player = (Player) (Object) this;
        if (player.level().isClientSide) return;

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (goal instanceof HaveShieldDisabledGoal) {
                lockout.completeGoal(goal, player);
            }
        }
    }


}
