package me.marin.lockout.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.marin.lockout.*;
import me.marin.lockout.client.LockoutBoard;
import me.marin.lockout.generator.BoardGenerator;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.GoalRegistry;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import me.marin.lockout.network.CustomBoardPayload;
import me.marin.lockout.network.LockoutVersionPayload;
import me.marin.lockout.network.StartLockoutPayload;
import me.marin.lockout.network.UpdateTooltipPayload;
import me.marin.lockout.server.handlers.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.scores.PlayerTeam;
import oshi.util.tuples.Pair;

import java.util.*;

public class LockoutServer {

    public static final int LOCATE_SEARCH = 750;
    public static final Map<ResourceKey<Biome>, LocateData> BIOME_LOCATE_DATA = new HashMap<>();
    public static final Map<ResourceKey<Structure>, LocateData> STRUCTURE_LOCATE_DATA = new HashMap<>();
    public static final List<DyeColor> AVAILABLE_DYE_COLORS = new ArrayList<>();

    private static int lockoutStartTime = 60;
    private static int boardSize;

    public static Lockout lockout;
    public static MinecraftServer server;
    public static CompassItemHandler compassHandler;

    public static final Map<LockoutRunnable, Long> gameStartRunnables = new HashMap<>();

    private static LockoutBoard CUSTOM_BOARD = null;

    private static boolean isInitialized = false;

    public static Map<ServerPlayer, Integer> waitingForVersionPacketPlayersMap = new HashMap<>();

    public static void initializeServer() {
        lockout = null;
        compassHandler = null;
        gameStartRunnables.clear();

        // Ideally, rejoining a world gets detected here, and this data doesn't get wiped
        BIOME_LOCATE_DATA.clear();
        STRUCTURE_LOCATE_DATA.clear();
        AVAILABLE_DYE_COLORS.clear();

        LockoutConfig.load(); // reload config every time the server starts
        boardSize = LockoutConfig.getInstance().boardSize;
        Lockout.log("Using default board size: " + boardSize);

        if (isInitialized) return;
        isInitialized = true;

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(new AllowChatMessageEventHandler());

        ServerPlayerEvents.AFTER_RESPAWN.register(new AfterRespawnEventHandler());

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(new AfterPlayerChangeWorldEventHandler());

        ServerPlayConnectionEvents.JOIN.register(new PlayerJoinEventHandler());

        ServerTickEvents.END_SERVER_TICK.register(new EndServerTickEventHandler());

        ServerLivingEntityEvents.AFTER_DEATH.register(new AfterDeathEventHandler());

        UseBlockCallback.EVENT.register(new UseBlockEventHandler());

        ServerLifecycleEvents.SERVER_STARTED.register(new ServerStartedEventHandler());

        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> {
            waitingForVersionPacketPlayersMap.remove(handler.getPlayer());
        });

        ServerPlayNetworking.registerGlobalReceiver(LockoutVersionPayload.ID, (payload, context) -> {
            // Client has Lockout mod, compare versions, then kick or initialize
            ServerPlayer player = context.player();
            waitingForVersionPacketPlayersMap.remove(player);

            String version = payload.version();
            if (!version.equals(LockoutInitializer.MOD_VERSION.getFriendlyString())) {
                player.connection.disconnect(Component.literal("Wrong Lockout version: v" + version + ".\nServer is using Lockout v" + LockoutInitializer.MOD_VERSION.getFriendlyString() + "."));
                return;
            }

            if (!Lockout.isLockoutRunning(lockout)) return;

            if (lockout.isLockoutPlayer(player.getUUID())) {
                LockoutTeamServer team = (LockoutTeamServer) lockout.getPlayerTeam(player.getUUID());
                for (Goal goal : lockout.getBoard().getGoals()) {
                    if (goal instanceof HasTooltipInfo hasTooltipInfo) {
                        ServerPlayNetworking.send(player, new UpdateTooltipPayload(goal.getId(), String.join("\n", hasTooltipInfo.getTooltip(team, player))));
                    }
                }
                player.setGameMode(GameType.SURVIVAL);
            } else {
                for (Goal goal : lockout.getBoard().getGoals()) {
                    if (goal instanceof HasTooltipInfo hasTooltipInfo) {
                        ServerPlayNetworking.send(player, new UpdateTooltipPayload(goal.getId(), String.join("\n", hasTooltipInfo.getSpectatorTooltip())));
                    }
                }
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal("You are spectating this match.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }

            ServerPlayNetworking.send(player, lockout.getTeamsGoalsPacket());
            ServerPlayNetworking.send(player, lockout.getUpdateTimerPacket());
            if (lockout.hasStarted()) {
                ServerPlayNetworking.send(player, StartLockoutPayload.INSTANCE);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(CustomBoardPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();

            if (!server.isSingleplayer()) {
                if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                    player.sendSystemMessage(Component.literal("You do not have the permission for this command!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            boolean clearBoard = payload.boardOrClear().isEmpty();
            if (clearBoard) {
                CUSTOM_BOARD = null;
                player.sendSystemMessage(Component.literal("Removed custom board."));
            } else {
                // validate board
                List<String> invalidGoals = new ArrayList<>();
                for (Pair<String, String> goal : payload.boardOrClear().get()) {
                    if (!GoalRegistry.INSTANCE.isGoalValid(goal.getA(), goal.getB())) {
                        invalidGoals.add(" - '" + goal.getA() + "'" + ("null".equals(goal.getB()) ? "" : (" with data: '" + goal.getB() + "'")));
                    }
                }
                if (!invalidGoals.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Invalid board. Could not create goals:\n" + String.join("\n", invalidGoals)));
                    return;
                }
                CUSTOM_BOARD = new LockoutBoard(payload.boardOrClear().get());
                player.sendSystemMessage(Component.literal("Set custom board."));
            }
        });
    }

    public static LocateData locateBiome(MinecraftServer server, ResourceKey<Biome> biome) {
        if (BIOME_LOCATE_DATA.containsKey(biome)) return BIOME_LOCATE_DATA.get(biome);

        var currentPos = server.overworld().getRespawnData().pos();

        var pair = server.overworld().findClosestBiome3d(
                biomeRegistryEntry -> biomeRegistryEntry.is(biome.identifier()),
                currentPos,
                LOCATE_SEARCH,
                32,
                64);

        LocateData data= new LocateData(false,0);
        if (pair != null) {
            int dx = pair.getFirst().getX() - currentPos.getX();
            int dz = pair.getFirst().getZ() - currentPos.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            if (distance < LOCATE_SEARCH) {
                data = new LocateData(true, distance);
            }
        }
        BIOME_LOCATE_DATA.put(biome, data);

        return data;
    }

    public static LocateData locateStructure(MinecraftServer server, ResourceKey<Structure> structure) {
        if (STRUCTURE_LOCATE_DATA.containsKey(structure)) return STRUCTURE_LOCATE_DATA.get(structure);

        var currentPos = server.overworld().getRespawnData().pos();

        HolderGetter<Structure> registry = server.overworld().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> structureList = HolderSet.direct(registry.getOrThrow(structure));

        var pair = server.overworld().getChunkSource().getGenerator().findNearestMapStructure(
                server.overworld(),
                structureList,
                currentPos,
                LOCATE_SEARCH,
                false);

        LocateData data = new LocateData(false, 0);
        if (pair != null) {
            int dx = pair.getFirst().getX() - currentPos.getX();
            int dz = pair.getFirst().getZ() - currentPos.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            if (distance < LOCATE_SEARCH) {
                data = new LocateData(true, distance);
            }
        }
        STRUCTURE_LOCATE_DATA.put(structure, data);

        return data;
    }

    public static int lockoutCommandLogic(CommandContext<CommandSourceStack> context) {
        List<LockoutTeamServer> teams = new ArrayList<>();

        int ret = parseArgumentsIntoTeams(teams, context, false);
        if (ret == 0) return 0;

        startLockout(teams);

        return 1;
    }

    public static int blackoutCommandLogic(CommandContext<CommandSourceStack> context) {
        List<LockoutTeamServer> teams = new ArrayList<>();

        int ret = parseArgumentsIntoTeams(teams, context, true);
        if (ret == 0) return 0;

        startLockout(teams);

        return 1;
    }

    private static void startLockout(List<LockoutTeamServer> teams) {
        // Clear old runnables
        gameStartRunnables.clear();

        PlayerList playerManager = server.getPlayerList();
        List<ServerPlayer> allServerPlayers = playerManager.getPlayers();
        List<UUID> allLockoutPlayers = teams.stream()
                .flatMap(team -> team.getPlayers().stream())
                .toList();
        List<UUID> allSpectatorPlayers = allServerPlayers.stream()
                .map(ServerPlayer::getUUID)
                .filter(uuid -> !allLockoutPlayers.contains(uuid))
                .toList();

        for (ServerPlayer serverPlayer : allServerPlayers) {
            serverPlayer.getInventory().clearContent();
            serverPlayer.setHealth(serverPlayer.getMaxHealth());
            serverPlayer.removeAllEffects();
            serverPlayer.getFoodData().setSaturation(5.0f);
            serverPlayer.getFoodData().setFoodLevel(20);
            serverPlayer.getFoodData().exhaustionLevel = 0.0f;
            serverPlayer.setExperienceLevels(0);
            serverPlayer.setExperiencePoints(0);
            serverPlayer.clearFire();

            // Clear all stats
            for (@SuppressWarnings("unchecked") StatType<Object> statType : new StatType[]{Stats.ITEM_CRAFTED, Stats.BLOCK_MINED, Stats.ITEM_USED, Stats.ITEM_BROKEN, Stats.ITEM_PICKED_UP, Stats.ITEM_DROPPED, Stats.ENTITY_KILLED, Stats.ENTITY_KILLED_BY, Stats.CUSTOM}) {
                for (Object value : statType.getRegistry()) {
                    serverPlayer.resetStat(statType.get(value));
                }
            }
            serverPlayer.getStats().sendStats(serverPlayer);
            // Clear all advancements
            PlayerAdvancements playerAdv = serverPlayer.getAdvancements();
            for (AdvancementHolder adv : server.getAdvancements().getAllAdvancements()) {
                for (String criterion : adv.value().criteria().keySet()) {
                    playerAdv.revoke(adv, criterion);
                }
            }

            if (allLockoutPlayers.contains(serverPlayer.getUUID())) {
                serverPlayer.setGameMode(GameType.ADVENTURE);
            } else {
                serverPlayer.setGameMode(GameType.SPECTATOR);
                serverPlayer.sendSystemMessage(Component.literal("You are spectating this match.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }

        // Generate & set board
        LockoutBoard lockoutBoard;
        if (CUSTOM_BOARD == null) {
            BoardGenerator boardGenerator = new BoardGenerator(GoalRegistry.INSTANCE.getRegisteredGoals(), teams, AVAILABLE_DYE_COLORS, BIOME_LOCATE_DATA, STRUCTURE_LOCATE_DATA);
            lockoutBoard = boardGenerator.generateBoard(boardSize);
        } else {
            // Reset custom board (TODO: do this somewhere else)
            for (Goal goal : CUSTOM_BOARD.getGoals()) {
                goal.setCompleted(false, null);
            }
            lockoutBoard = CUSTOM_BOARD;
        }

        lockout = new Lockout(lockoutBoard, teams);
        lockout.setTicks(-20L * lockoutStartTime); // see Lockout#ticks

        compassHandler = new CompassItemHandler(allLockoutPlayers, playerManager);

        List<Goal> tooltipGoals = new ArrayList<>(lockout.getBoard().getGoals()).stream().filter(g -> g instanceof HasTooltipInfo).toList();
        for (Goal goal : tooltipGoals) {
            // Update teams tooltip
            for (LockoutTeam team : lockout.getTeams()) {
                ((LockoutTeamServer) team).sendTooltipUpdate((Goal & HasTooltipInfo) goal, false);
            }
            // Update spectator tooltip
            if (!allSpectatorPlayers.isEmpty()) {
                var payload = new UpdateTooltipPayload(goal.getId(), String.join("\n", ((HasTooltipInfo) goal).getSpectatorTooltip()));
                for (UUID spectator : allSpectatorPlayers) {
                    ServerPlayNetworking.send(playerManager.getPlayer(spectator), payload);
                }
            }
        }

        for (ServerPlayer player : allServerPlayers) {
            ServerPlayNetworking.send(player, lockout.getTeamsGoalsPacket());
            ServerPlayNetworking.send(player, lockout.getUpdateTimerPacket());

            if (!lockout.isSoloBlackout() && lockout.isLockoutPlayer(player.getUUID())) {
                player.addItem(compassHandler.newCompass());
            }
        }

        for (int i = 3; i >= 0; i--) {
            if (i > 0) {
                final int secs = i;
                ((LockoutRunnable) () -> {
                    playerManager.broadcastSystemMessage(Component.literal("Starting in " + secs + "..."), false);
                }).runTaskAfter(20L * (lockoutStartTime - i));
            } else {
                ((LockoutRunnable) () -> {
                    lockout.setStarted(true);

                    for (ServerPlayer player : allServerPlayers) {
                        if (player == null) continue;
                        ServerPlayNetworking.send(player, StartLockoutPayload.INSTANCE);
                        if (allLockoutPlayers.contains(player.getUUID())) {
                            player.setGameMode(GameType.SURVIVAL);
                        }
                    }
                    server.getPlayerList().broadcastSystemMessage(Component.literal(lockout.getModeName() + " has begun."), false);
                }).runTaskAfter(20L * lockoutStartTime);
            }
        }
    }

    private static int parseArgumentsIntoTeams(List<LockoutTeamServer> teams, CommandContext<CommandSourceStack> context, boolean isBlackout) {
        String argument = null;

        PlayerList playerManager = server.getPlayerList();

        try {
            argument = context.getArgument("player names", String.class);
            String[] players = argument.split(" +");
            if (isBlackout) {
                if (players.length == 0) {
                    context.getSource().sendFailure(Component.literal("Not enough players listed."));
                    return 0;
                }

                List<String> playerNames = new ArrayList<>();
                for (String player : players) {
                    if (playerManager.getPlayer(player) == null) {
                        context.getSource().sendFailure(Component.literal("Player " + player + " is invalid."));
                        return 0;
                    }
                    playerNames.add(playerManager.getPlayer(player).getName().getString());
                }
                teams.add(new LockoutTeamServer(playerNames, ChatFormatting.getById(Lockout.COLOR_ORDERS[0]), server));

            } else {
                if (players.length < 2) {
                    context.getSource().sendFailure(Component.literal("Not enough players listed. Make sure you separate player names with spaces."));
                    return 0;
                }
                if (players.length > 16) {
                    context.getSource().sendFailure(Component.literal("Too many players listed."));
                    return 0;
                }

                for (int i = 0; i < players.length; i++) {
                    String player = players[i];
                    if (playerManager.getPlayer(player) == null) {
                        context.getSource().sendFailure(Component.literal("Player " + player + " is invalid."));
                        return 0;
                    }
                    teams.add(new LockoutTeamServer(List.of(playerManager.getPlayer(player).getName().getString()), ChatFormatting.getById(Lockout.COLOR_ORDERS[i]), server));
                }
            }

        } catch (Exception ignored) {}

        if (argument == null) {
            try {
                ServerScoreboard scoreboard = server.getScoreboard();

                argument = context.getArgument(isBlackout ? "team name" : "team names", String.class);
                String[] teamNames = argument.split(" +");
                if (isBlackout) {
                    if (teamNames.length == 0) {
                        context.getSource().sendFailure(Component.literal("Not enough teams listed."));
                        return 0;
                    }
                    if (teamNames.length > 1) {
                        context.getSource().sendFailure(Component.literal("Only one team can play Blackout."));
                        return 0;
                    }
                } else {
                    if (teamNames.length < 2) {
                        context.getSource().sendFailure(Component.literal("Not enough teams listed. Make sure you separate team names with spaces."));
                        return 0;
                    }
                    if (teamNames.length > 16) {
                        context.getSource().sendFailure(Component.literal("Too many teams listed."));
                        return 0;
                    }
                }

                List<PlayerTeam> scoreboardTeams = new ArrayList<>();
                for (String teamName : teamNames) {
                    PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                    if (team == null) {
                        context.getSource().sendFailure(Component.literal("Team " + teamName + " is invalid."));
                        return 0;
                    }
                    for (String player : team.getPlayers()) {
                        if (playerManager.getPlayer(player) == null) {
                            context.getSource().sendFailure(Component.literal("Player " + player + " on team " + teamName + " is invalid. Remove them from the team and try again."));
                            return 0;
                        }
                    }
                    scoreboardTeams.add(team);
                }
                for (PlayerTeam team : scoreboardTeams) {
                    if (team.getPlayers().isEmpty()) {
                        context.getSource().sendFailure(Component.literal("Team " + team.getName() + " doesn't have any players."));
                        return 0;
                    }
                    ChatFormatting teamColor = team.getColor();
                    if (teamColor.getColor() == null || teamHasColor(teams, teamColor)) {
                        // Select an available color.
                        boolean found = false;
                        for (int colorOrder : Lockout.COLOR_ORDERS) {
                            if (!teamHasColor(teams, ChatFormatting.getById(colorOrder))) {
                                found = true;
                                team.setColor(ChatFormatting.getById(colorOrder));
                                break;
                            }
                        }
                        if (!found) {
                            context.getSource().sendFailure(Component.literal("Could not find assignable color for team " + team.getName() + ". Try recreating teams."));
                            return 0;
                        }
                    }
                    List<String> actualPlayerNames = new ArrayList<>();
                    for (String playerName : team.getPlayers()) {
                        actualPlayerNames.add(playerManager.getPlayer(playerName).getName().getString());
                    }
                    teams.add(new LockoutTeamServer(new ArrayList<>(actualPlayerNames), team.getColor(), server));
                }
            } catch (Exception ignored) {}
        }

        if (argument == null) {
            context.getSource().sendFailure(Component.literal("Illegal argument."));
            return 0;
        }
        return 1;
    }

    private static boolean teamHasColor(List<LockoutTeamServer> teams, ChatFormatting color) {
        for (LockoutTeam lockoutTeam : teams) {
            if (lockoutTeam.getColor() == color) {
                return true;
            }
        }
        return false;
    }

    public static int setChat(CommandContext<CommandSourceStack> context, ChatManager.Type type) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("This is a player-only command."));
            return 0;
        }

        ChatManager.Type curr = ChatManager.getChat(player);
        if (curr == type) {
            player.sendSystemMessage(Component.literal("You are already chatting in " + type.name() + "."));
        } else {
            player.sendSystemMessage(Component.literal("You are now chatting in " + type.name() + "."));
            ChatManager.setChat(player, type);
        }
        return 1;
    }

    public static int giveGoal(CommandContext<CommandSourceStack> context) {
        try {
            if (!Lockout.isLockoutRunning(lockout)) {
                context.getSource().sendFailure(Component.literal("There's no active lockout match."));
                return 0;
            }

            int idx = context.getArgument("goal number", Integer.class);

            Collection<NameAndId> gps;
            try {
                gps = GameProfileArgument.getGameProfiles(context, "player name");
            } catch (CommandSyntaxException e) {
                context.getSource().sendFailure(Component.literal("Invalid target."));
                return 0;
            }

            if (gps.size() != 1) {
                context.getSource().sendFailure(Component.literal("Invalid number of targets."));
                return 0;
            }
            NameAndId gp = gps.stream().findFirst().get();
            if (!lockout.isLockoutPlayer(gp.id())) {
                context.getSource().sendFailure(Component.literal("Player " + gp.name() + " is not playing Lockout."));
                return 0;
            }

            if (idx > lockout.getBoard().getGoals().size()) {
                context.getSource().sendFailure(Component.literal("Goal number does not exist on the board."));
                return 0;
            }
            Goal goal = lockout.getBoard().getGoals().get(idx - 1);

            context.getSource().sendSuccess(() -> Component.literal("Gave " + gp.name() + " goal \"" + goal.getGoalName() + "\"."), false);
            lockout.updateGoalCompletion(goal, gp.id());
            return 1;
        } catch (RuntimeException e) {
            Lockout.error(e);
            return 0;
        }
    }

    public static int setStartTime(CommandContext<CommandSourceStack> context) {
        int seconds = context.getArgument("seconds", Integer.class);

        lockoutStartTime = seconds;
        context.getSource().sendSuccess(() -> Component.literal("Updated start time to " + seconds + "s."), false);
        return 1;
    }

    public static int setBoardSize(CommandContext<CommandSourceStack> context) {
        int size = context.getArgument("board size", Integer.class);

        boardSize = size;
        context.getSource().sendSuccess(() -> Component.literal("Updated board size to " + size + "."), false);
        return 1;
    }

}