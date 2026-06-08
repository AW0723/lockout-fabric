package me.marin.lockout;

import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import me.marin.lockout.network.UpdateTooltipPayload;
import me.marin.lockout.server.LockoutServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;

public class LockoutTeamServer extends LockoutTeam {

    private final List<UUID> players = new ArrayList<>();
    private final Map<UUID, String> playerNameMap = new HashMap<>();
    private final MinecraftServer server;

    public List<UUID> getPlayers() { return players; }
    public MinecraftServer getServer() { return server; }

    public LockoutTeamServer(List<String> playerNames, ChatFormatting formattingColor, MinecraftServer server) {
        super(playerNames, formattingColor);
        this.server = server;

        PlayerList manager = server.getPlayerList();

        // All players from playerNames are online at this moment.
        for (String playerName : playerNames) {
            this.players.add(manager.getPlayerByName(playerName).getUUID());
            this.playerNameMap.put(manager.getPlayerByName(playerName).getUUID(), playerName);
        }
    }

    public String getPlayerName(UUID uuid) {
        return playerNameMap.get(uuid);
    }

    public void sendMessage(String message) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    public <T extends Goal & HasTooltipInfo> void sendTooltipUpdate(T goal) {
        sendTooltipUpdate(goal, true);
    }
    public <T extends Goal & HasTooltipInfo> void sendTooltipUpdate(T goal, boolean updateSpectators) {
        for (UUID playerId : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            var payload = new UpdateTooltipPayload(goal.getId(), String.join("\n", goal.getTooltip(this, player)));
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }

        if (updateSpectators) {
            this.sendTooltipPacketSpectators(goal);
        }
    }
    private <T extends Goal & HasTooltipInfo> void sendTooltipPacketSpectators(T goal) {
        var payload = new UpdateTooltipPayload(goal.getId(), String.join("\n", goal.getSpectatorTooltip()));
        for (ServerPlayer spectator : Utility.getSpectators(LockoutServer.lockout, server)) {
            ServerPlayNetworking.send(spectator, payload);
        }
    }

}
