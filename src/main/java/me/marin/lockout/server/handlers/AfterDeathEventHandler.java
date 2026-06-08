package me.marin.lockout.server.handlers;
import net.minecraft.world.level.Level;

import me.marin.lockout.Lockout;
import me.marin.lockout.LockoutTeam;
import me.marin.lockout.LockoutTeamServer;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.death.DieToFallingOffVinesGoal;
import me.marin.lockout.lockout.goals.death.DieToTNTMinecartGoal;
import me.marin.lockout.lockout.goals.kill.*;
import me.marin.lockout.lockout.goals.opponent.OpponentDies3TimesGoal;
import me.marin.lockout.lockout.goals.opponent.OpponentDiesGoal;
import me.marin.lockout.lockout.interfaces.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.FallLocation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static me.marin.lockout.server.LockoutServer.lockout;

public class AfterDeathEventHandler implements ServerLivingEntityEvents.AfterDeath {
    @Override
    public void afterDeath(LivingEntity entity, DamageSource source) {
        if (!Lockout.isLockoutRunning(lockout)) {
            return;
        }
        if (entity instanceof Player player && !lockout.isLockoutPlayer(player)) {
            return;
        }

        boolean playerDied = entity instanceof Player;
        boolean mobDied = !playerDied;
        boolean killedByPlayer = entity.getKillCredit() instanceof Player;

        if (playerDied) {
            LockoutTeam team = lockout.getPlayerTeam(entity.getUUID());

            lockout.deaths.putIfAbsent(team, 0);
            lockout.deaths.merge(team, 1, Integer::sum);
        }
        if (mobDied && killedByPlayer) {
            Player killer = (Player) entity.getKillCredit();
            if (lockout.isLockoutPlayer(killer.getUUID())) {
                LockoutTeam team = lockout.getPlayerTeam(killer.getUUID());
                lockout.mobsKilled.putIfAbsent(team, 0);
                lockout.mobsKilled.merge(team, 1, Integer::sum);
            }
        }

        for (Goal goal : lockout.getBoard().getGoals()) {
            if (goal == null) continue;
            if (goal.isCompleted()) continue;

            if (mobDied && killedByPlayer) {
                Player killer = (Player) entity.getKillCredit();

                if (goal instanceof KillMobGoal killMobGoal) {
                    if (killMobGoal.getEntity().equals(entity.getType())) {
                        boolean allow = true;
                        if (goal instanceof KillSnowGolemInNetherGoal)  {
                            allow = killer.level().dimension() == Level.NETHER;
                        }
                        if (goal instanceof KillBreezeWithWindChargeGoal) {
                            allow = source.is(DamageTypes.WIND_CHARGE);
                        }
                        if (goal instanceof KillColoredSheepGoal killColoredSheepGoal) {
                            allow = ((Sheep) entity).getColor() == killColoredSheepGoal.getDyeColor();
                        }
                        if (allow) {
                            lockout.completeGoal(goal, killer);
                        }
                    }
                }
                LockoutTeamServer team = (LockoutTeamServer) lockout.getPlayerTeam(killer.getUUID());

                if (goal instanceof KillAllSpecificMobsGoal killAllSpecificMobsGoal) {
                    if (killAllSpecificMobsGoal.getEntityTypes().contains(entity.getType())) {
                        killAllSpecificMobsGoal.getTrackerMap().computeIfAbsent(team, t -> new LinkedHashSet<>());
                        killAllSpecificMobsGoal.getTrackerMap().get(team).add(entity.getType());

                        int size = killAllSpecificMobsGoal.getTrackerMap().get(team).size();

                        team.sendTooltipUpdate((Goal & HasTooltipInfo) goal);
                        if (size >= killAllSpecificMobsGoal.getEntityTypes().size()) {
                            lockout.completeGoal(killAllSpecificMobsGoal, team);
                        }
                    }
                }
                if (goal instanceof KillUniqueHostileMobsGoal killUniqueHostileMobsGoal) {
                    if (entity instanceof Monster) {
                        lockout.killedHostileTypes.computeIfAbsent(team, t -> new LinkedHashSet<>());
                        lockout.killedHostileTypes.get(team).add(entity.getType());

                        int size = lockout.killedHostileTypes.get(team).size();

                        team.sendTooltipUpdate((Goal & HasTooltipInfo) goal);
                        if (size >= killUniqueHostileMobsGoal.getAmount()) {
                            lockout.completeGoal(killUniqueHostileMobsGoal, team);
                        }
                    }
                }
                if (goal instanceof Kill100MobsGoal kill100MobsGoal) {
                    int size = lockout.mobsKilled.get(team);

                    team.sendTooltipUpdate((Goal & HasTooltipInfo) goal);
                    if (size >= kill100MobsGoal.getAmount()) {
                        lockout.completeGoal(goal, team);
                    }
                }
                if (goal instanceof KillSpecificMobsGoal killSpecificMobsGoal) {
                    if (killSpecificMobsGoal.getEntityTypes().contains(entity.getType())) {
                        killSpecificMobsGoal.getTrackerMap().computeIfAbsent(team, t -> 0);
                        killSpecificMobsGoal.getTrackerMap().merge(team, 1, Integer::sum);

                        int size = killSpecificMobsGoal.getTrackerMap().get(team);

                        team.sendTooltipUpdate((Goal & HasTooltipInfo) goal);
                        if (size >= killSpecificMobsGoal.getAmount()) {
                            lockout.completeGoal(killSpecificMobsGoal, killer);
                        }
                    }
                }
            }
            if (playerDied) {
                Player player = (Player) entity;
                LockoutTeam team = lockout.getPlayerTeam(player.getUUID());

                if (goal instanceof OpponentDiesGoal) {
                    lockout.complete1v1Goal(goal, player, false, player.getName().getString() + " died.");
                }
                if (goal instanceof OpponentDies3TimesGoal && lockout.deaths.get(team) >= 3) {
                    lockout.complete1v1Goal(goal, player, false, team.getDisplayName() + " died 3 times.");
                }
                if (goal instanceof DieToDamageTypeGoal dieToDamageTypeGoal) {
                    for (ResourceKey<DamageType> key : dieToDamageTypeGoal.getDamageRegistryKeys()) {
                        if (source.is(key)) {
                            lockout.completeGoal(goal, player);
                        }
                    }
                }
                if (goal instanceof DieToEntityGoal dieToEntityGoal) {
                    if (source.getEntity() != null && source.getEntity().getType() == dieToEntityGoal.getEntityType()) {
                        lockout.completeGoal(goal, player);
                    }
                }
                if (goal instanceof DieToFallingOffVinesGoal) {
                    if (source.is(DamageTypes.FALL)) {
                        FallLocation fallLocation = FallLocation.getCurrentFallLocation(player);
                        if (fallLocation != null) {
                            if (List.of(FallLocation.VINES, FallLocation.TWISTING_VINES, FallLocation.WEEPING_VINES).contains(fallLocation)) {
                                lockout.completeGoal(goal, player);
                            }
                        }
                    }
                }
                if (goal instanceof DieToTNTMinecartGoal) {
                    if (source.getDirectEntity() instanceof MinecartTNT) {
                        lockout.completeGoal(goal, player);
                    }
                }

                if (goal instanceof KillOtherTeamPlayer && killedByPlayer) {
                    Player killer = (Player) entity.getKillCredit();

                    if (!Objects.equals(player, killer) && !Objects.equals(lockout.getPlayerTeam(killer.getUUID()), lockout.getPlayerTeam(player.getUUID()))) {
                        lockout.completeGoal(goal, killer);
                    }
                }
            }


        }

    }
}
