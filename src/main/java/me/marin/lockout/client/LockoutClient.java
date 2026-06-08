package me.marin.lockout.client;

import me.marin.lockout.*;
import me.marin.lockout.client.gui.*;
import me.marin.lockout.json.JSONBoard;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.goals.util.GoalDataConstants;
import me.marin.lockout.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static me.marin.lockout.Constants.MAX_BOARD_SIZE;
import static me.marin.lockout.Constants.MIN_BOARD_SIZE;

public class LockoutClient implements ClientModInitializer {

    public static Lockout lockout;
    public static boolean amIPlayingLockout = false;
    private static KeyMapping keyBinding;
    public static int CURRENT_TICK = 0;
    public static final Map<String, String> goalTooltipMap = new HashMap<>();

    public static final MenuType<BoardScreenHandler> BOARD_SCREEN_HANDLER;

    static {
        BOARD_SCREEN_HANDLER = new MenuType<>(BoardScreenHandler::new, FeatureFlags.VANILLA_SET);
    }

    @Override
    public void onInitializeClient() {
        Registry.register(BuiltInRegistries.MENU, Constants.BOARD_SCREEN_ID, BOARD_SCREEN_HANDLER);

        ClientPlayNetworking.registerGlobalReceiver(LockoutGoalsTeamsPayload.ID, (payload, context) -> {
            List<LockoutTeam> teams = payload.teams();

            LockoutClient.amIPlayingLockout = teams.stream().map(LockoutTeam::getPlayerNames)
                    .anyMatch(players -> players.stream().anyMatch(player -> player.equals(Minecraft.getInstance().getUser().getName())));

            int[] completedByTeam = payload.goals().stream().mapToInt(Pair::getB).toArray();

            lockout = new Lockout(new LockoutBoard(payload.goals().stream().map(Pair::getA).toList()), teams);
            lockout.setRunning(payload.isRunning());

            List<Goal> goalList = lockout.getBoard().getGoals();
            for (int i = 0; i < goalList.size(); i++) {
                if (completedByTeam[i] != -1) {
                    LockoutTeam team = lockout.getTeams().get(completedByTeam[i]);
                    goalList.get(i).setCompleted(true, team);
                    team.addPoint();
                }
            }

            Minecraft client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    client.setScreen(new BoardScreen(BOARD_SCREEN_HANDLER.create(0, client.player.getInventory()), client.player.getInventory(), Component.empty()));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateTooltipPayload.ID, (payload, context) -> {
            goalTooltipMap.put(payload.goal(), payload.tooltip());
        });
        ClientPlayNetworking.registerGlobalReceiver(StartLockoutPayload.ID, (payload, context) -> {
            lockout.setStarted(true);
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.onClose();
                    Minecraft.getInstance().setScreen(null);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(UpdateTimerPayload.ID, (payload, context) -> {
            lockout.setTicks(payload.ticks());
        });
        ClientPlayNetworking.registerGlobalReceiver(CompleteTaskPayload.ID, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                Goal goal = lockout.getBoard().getGoals().stream().filter(g -> g.getId().equals(payload.goal())).findFirst().get();
                if (goal.isCompleted() || payload.teamIndex() == -1) {
                    lockout.clearGoalCompletion(goal, false);
                }
                if (payload.teamIndex() != -1) {
                    LockoutTeam team = lockout.getTeams().get(payload.teamIndex());
                    team.addPoint();
                    goal.setCompleted(true, lockout.getTeams().get(payload.teamIndex()));

                    if (client.player != null && amIPlayingLockout) {
                        if (team.getPlayerNames().contains(client.player.getName().getString())) {
                            client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2f, 1f);
                        } else {
                            client.player.playSound(SoundEvents.GUARDIAN_DEATH, 2f, 1f);
                        }
                    }
                }


            });
        });
        ClientPlayNetworking.registerGlobalReceiver(EndLockoutPayload.ID, (payload, context) -> {
            lockout.setRunning(false);
            Minecraft client = context.client();
            client.execute(() -> {
                if (client.player != null) {
                    boolean didIWin = false;
                    for (int winner : payload.winners()) {
                        LockoutTeam team = lockout.getTeams().get(winner);

                        if (team.getPlayerNames().contains(client.player.getName().getString())) {
                            didIWin = true;
                            break;
                        }
                    }
                    if (didIWin) {
                        client.player.playSound(SoundEvents.PILLAGER_CELEBRATE, 2f, 1f);
                    } else {
                        client.player.playSound(SoundEvents.WARDEN_DEATH, 2f, 1f);
                    }
                }
            });
        });

        ArgumentTypeRegistry.registerArgumentType(Constants.BOARD_FILE_ARGUMENT_TYPE, CustomBoardFileArgumentType.class, SingletonArgumentInfo.contextFree(CustomBoardFileArgumentType::newInstance));
        ArgumentTypeRegistry.registerArgumentType(Constants.BOARD_POSITION_ARGUMENT_TYPE, BoardPositionArgumentType.class, SingletonArgumentInfo.contextFree(BoardPositionArgumentType::newInstance));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            {
                var commandNode = ClientCommands.literal("BoardPosition").build();
                var positionNode = ClientCommands.argument("board position", BoardPositionArgumentType.newInstance()).executes((context) -> {
                    String position = context.getArgument("board position", String.class);

                    LockoutConfig.BoardPosition boardPosition = LockoutConfig.BoardPosition.match(position);
                    if (boardPosition == null) {
                        context.getSource().sendError(Component.literal("Invalid board position: " + position + "."));
                        return 0;
                    }
                    LockoutConfig.getInstance().boardPosition = boardPosition;
                    LockoutConfig.save();

                    context.getSource().sendFeedback(Component.literal("Updated board position." + (boardPosition == LockoutConfig.BoardPosition.LEFT ? " Note: Opening debug hud (F3) will hide the board." : "")));

                    return 1;
                }).build();

                dispatcher.getRoot().addChild(commandNode);
                commandNode.addChild(positionNode);
            }
            {
                var commandNode = ClientCommands.literal("BoardBuilder").executes((context) -> {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (client.player != null) {
                            client.setScreen(new BoardBuilderScreen());
                        }
                    });

                    return 1;
                }).build();

                var boardNameNode = ClientCommands.argument("board name", CustomBoardFileArgumentType.newInstance()).executes((context) -> {
                    String boardName = context.getArgument("board name", String.class);

                    JSONBoard jsonBoard;
                    try {
                        jsonBoard = BoardBuilderIO.INSTANCE.readBoard(boardName);
                    } catch (IOException e) {
                        context.getSource().sendError(Component.literal("Error while trying to read board."));
                        return 0;
                    }

                    int size = (int) Math.sqrt(jsonBoard.goals.size());
                    if (size * size != jsonBoard.goals.size() || size < MIN_BOARD_SIZE || size > MAX_BOARD_SIZE) {
                        context.getSource().sendError(Component.literal("Board doesn't have a valid number of goals!"));
                        return 0;
                    }

                    List<Pair<String, String>> goals = jsonBoard.goals.stream()
                            .map(goal -> new Pair<>(goal.id, goal.data != null ? goal.data : GoalDataConstants.DATA_NONE)).toList();

                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (client.player != null) {
                            BoardBuilderData.INSTANCE.setBoard(boardName, size, goals);
                            client.setScreen(new BoardBuilderScreen());
                        }
                    });

                    return 1;
                }).build();

                commandNode.addChild(boardNameNode);
                dispatcher.getRoot().addChild(commandNode);
            }
            {
                var commandNode = ClientCommands.literal("SetCustomBoard").requires(ccs -> true).build();

                var boardNameNode = ClientCommands.argument("board name", CustomBoardFileArgumentType.newInstance()).executes((context) -> {
                    String boardName = context.getArgument("board name", String.class);

                    JSONBoard jsonBoard;
                    try {
                        jsonBoard = BoardBuilderIO.INSTANCE.readBoard(boardName);
                    } catch (IOException e) {
                        context.getSource().sendError(Component.literal("Error while trying to read board."));
                        return 0;
                    }

                    int size = (int) Math.sqrt(jsonBoard.goals.size());
                    if (size * size != jsonBoard.goals.size() || size < MIN_BOARD_SIZE || size > MAX_BOARD_SIZE) {
                        context.getSource().sendError(Component.literal("Board doesn't have a valid number of goals!"));
                        return 0;
                    }

                    ClientPlayNetworking.send(new CustomBoardPayload(Optional.of(jsonBoard.goals.stream()
                            .map(goal -> new Pair<>(goal.id, goal.data != null ? goal.data : GoalDataConstants.DATA_NONE)).toList())));
                    return 1;
                }).build();

                commandNode.addChild(boardNameNode);
                dispatcher.getRoot().addChild(commandNode);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(LockoutVersionPayload.ID, (payload, context) -> {
            // Compare Lockout versions, disconnect if invalid.
            String version = payload.version();
            if (!version.equals(LockoutInitializer.MOD_VERSION.getFriendlyString())) {
                Minecraft.getInstance().player.connection.getConnection().disconnect(Component.literal("Wrong Lockout version: v" + LockoutInitializer.MOD_VERSION.getFriendlyString() + ".\nServer is using Lockout v" + version + "."));
                return;
            }

            // Respond with version, it will be compared on server as well
            ClientPlayNetworking.send(new LockoutVersionPayload(LockoutInitializer.MOD_VERSION.getFriendlyString()));
        });

        KeyMapping.Category lockoutCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lockout", "keybinds"));
        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.lockout.open_board",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                lockoutCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CURRENT_TICK++;

            boolean wasPressed = false;
            while (keyBinding.consumeClick()) {
                wasPressed = true;
            }
            if (wasPressed) {
                if (client.screen != null || client.player == null) {
                    return;
                }

                // If the game hasn't started, open board builder instead
                if (!Lockout.exists(lockout)) {
                    client.setScreen(new BoardBuilderScreen());
                    return;
                }

                // Open GUI
                client.setScreen(new BoardScreen(BOARD_SCREEN_HANDLER.create(0, client.player.getInventory()), client.player.getInventory(), Component.empty()));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LockoutConfig.load(); // reload config every time player joins world

        });
        ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> {
            lockout = null;
            goalTooltipMap.clear();
        }));

        MenuScreens.register(BOARD_SCREEN_HANDLER, BoardScreen::new);
    }

}
