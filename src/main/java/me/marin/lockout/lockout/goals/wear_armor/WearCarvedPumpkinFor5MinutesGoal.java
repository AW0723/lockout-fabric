package me.marin.lockout.lockout.goals.wear_armor;

import me.marin.lockout.LockoutTeam;
import me.marin.lockout.LockoutTeamServer;
import me.marin.lockout.Utility;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import me.marin.lockout.lockout.interfaces.WearArmorPieceGoal;
import me.marin.lockout.mixin.server.PlayerInventoryAccessor;
import me.marin.lockout.server.LockoutServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WearCarvedPumpkinFor5MinutesGoal extends WearArmorPieceGoal implements HasTooltipInfo {

    private static final List<Item> ITEMS = List.of(Items.CARVED_PUMPKIN);
    private static final int FIVE_MINUTES_TICKS = 20 * 60 * 5;

    public WearCarvedPumpkinFor5MinutesGoal(String id, String data) {
        super(id, data);
    }

    @Override
    public String getGoalName() {
        return "Wear a Carved Pumpkin for 5 minutes";
    }

    @Override
    public List<Item> getItems() {
        return ITEMS;
    }

    @Override
    public boolean satisfiedBy(Inventory playerInventory) {
        Player player = playerInventory.player;
        var map = LockoutServer.lockout.pumpkinWearTime;

        long wornTime = map.getOrDefault(player.getUUID(), 0L);

        // TODO: Do better
        var armor = new ArrayList<ItemStack>();
        armor.add(((PlayerInventoryAccessor)playerInventory).getEquipment().get(EquipmentSlot.HEAD));
        armor.add(((PlayerInventoryAccessor)playerInventory).getEquipment().get(EquipmentSlot.CHEST));
        armor.add(((PlayerInventoryAccessor)playerInventory).getEquipment().get(EquipmentSlot.LEGS));
        armor.add(((PlayerInventoryAccessor)playerInventory).getEquipment().get(EquipmentSlot.FEET));

        boolean wearingPumpkin = false;
        for (ItemStack item : armor) {
            if (item == null) continue;
            if (item.getItem() == Items.CARVED_PUMPKIN) {
                wearingPumpkin = true;
                break;
            }
        }

        if (wearingPumpkin) {
            wornTime += 1;
            map.put(player.getUUID(), wornTime);

            if (wornTime % 20 == 0) {
                ((LockoutTeamServer) LockoutServer.lockout.getPlayerTeam(player.getUUID())).sendTooltipUpdate(this, true);
            }

            return wornTime >= (FIVE_MINUTES_TICKS);
        }

        return false;
    }


    @Override
    public List<String> getTooltip(LockoutTeam team, Player player) {
        List<String> tooltip = new ArrayList<>();
        long timeWorn = Math.min(FIVE_MINUTES_TICKS, LockoutServer.lockout.pumpkinWearTime.getOrDefault(player.getUUID(), 0L));
        LockoutTeamServer serverTeam = ((LockoutTeamServer) team);

        tooltip.add(" ");
        tooltip.add("Time worn: " + Utility.ticksToTimer(timeWorn));
        if (serverTeam.getPlayers().size() > 1) {
            tooltip.add(" ");
            for (UUID uuid : ((LockoutTeamServer) team).getPlayers()) {
                if (!Objects.equals(uuid, player.getUUID())) {
                    tooltip.add(serverTeam.getPlayerName(uuid) + ": " + Utility.ticksToTimer(timeWorn));
                }
            }
        }
        tooltip.add(" ");

        return tooltip;
    }

    @Override
    public List<String> getSpectatorTooltip() {
        List<String> tooltip = new ArrayList<>();

        tooltip.add(" ");
        for (LockoutTeam team : LockoutServer.lockout.getTeams()) {
            for (UUID uuid : ((LockoutTeamServer) team).getPlayers()) {
                long timeWorn = Math.min(FIVE_MINUTES_TICKS, LockoutServer.lockout.pumpkinWearTime.getOrDefault(uuid, 0L));
                tooltip.add(team.getColor() + ((LockoutTeamServer) team).getPlayerName(uuid) + ChatFormatting.RESET + ": " + Utility.ticksToTimer(timeWorn));
            }
        }
        tooltip.add(" ");

        return tooltip;
    }
}
