package me.marin.lockout;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.marin.lockout.lockout.DefaultGoalRegister;
import me.marin.lockout.network.CustomBoardPayload;
import me.marin.lockout.network.Networking;
import me.marin.lockout.server.LockoutServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static me.marin.lockout.Constants.MAX_BOARD_SIZE;
import static me.marin.lockout.Constants.NAMESPACE;

public class LockoutInitializer implements ModInitializer {

    private static final Predicate<CommandSourceStack> PERMISSIONS = (ssc) ->
            ssc.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || (ssc.getServer() != null && ssc.getServer().isSingleplayer());

    public static Version MOD_VERSION;

    @Override
    public void onInitialize() {
        MOD_VERSION = FabricLoader.getInstance().getModContainer(NAMESPACE).get().getMetadata().getVersion();

        LockoutConfig.load();
        Networking.registerPayloads();
        DefaultGoalRegister.registerGoals();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            {
                {
                    // Lockout command
                    var commandNode = Commands.literal("lockout").requires(PERMISSIONS).build();
                    var teamsNode = Commands.literal("teams").build();
                    var playersNode = Commands.literal("players").build();
                    var teamListNode = Commands.argument("team names", StringArgumentType.greedyString()).executes(LockoutServer::lockoutCommandLogic).build();
                    var playerListNode = Commands.argument("player names", StringArgumentType.greedyString()).executes(LockoutServer::lockoutCommandLogic).build();

                    dispatcher.getRoot().addChild(commandNode);
                    commandNode.addChild(teamsNode);
                    commandNode.addChild(playersNode);
                    teamsNode.addChild(teamListNode);
                    playersNode.addChild(playerListNode);
                }


                {
                    // Blackout command
                    var commandNode = Commands.literal("blackout").requires(PERMISSIONS).build();
                    var teamNode = Commands.literal("team").build();
                    var playersNode = Commands.literal("players").build();
                    var teamNameNode = Commands.argument("team name", StringArgumentType.greedyString()).executes(LockoutServer::blackoutCommandLogic).build();
                    var playerListNode = Commands.argument("player names", StringArgumentType.greedyString()).executes(LockoutServer::blackoutCommandLogic).build();

                    dispatcher.getRoot().addChild(commandNode);
                    commandNode.addChild(teamNode);
                    commandNode.addChild(playersNode);
                    teamNode.addChild(teamNameNode);
                    playersNode.addChild(playerListNode);
                }
            }


            {
                // Chat command
                var chatCommandNode = Commands.literal("chat").build();
                var chatTeamNode = Commands.literal("team").executes(context -> LockoutServer.setChat(context, ChatManager.Type.TEAM)).build();
                var chatLocalNode = Commands.literal("local").executes(context -> LockoutServer.setChat(context, ChatManager.Type.LOCAL)).build();

                dispatcher.getRoot().addChild(chatCommandNode);
                chatCommandNode.addChild(chatTeamNode);
                chatCommandNode.addChild(chatLocalNode);
            }


            {
                // GiveGoal command
                var giveGoalRoot = Commands.literal("GiveGoal").requires(PERMISSIONS).build();
                var playerName = Commands.argument("player name", GameProfileArgument.gameProfile()).build();
                var goalIndex = Commands.argument("goal number", IntegerArgumentType.integer(1, MAX_BOARD_SIZE * MAX_BOARD_SIZE)).executes(LockoutServer::giveGoal).build();

                dispatcher.getRoot().addChild(giveGoalRoot);
                giveGoalRoot.addChild(playerName);
                playerName.addChild(goalIndex);
            }

            {
                // SetStartTime command
                var setStartTimeRoot = Commands.literal("SetStartTime").requires(PERMISSIONS).build();
                var seconds = Commands.argument("seconds", IntegerArgumentType.integer(5, 300)).executes(LockoutServer::setStartTime).build();

                dispatcher.getRoot().addChild(setStartTimeRoot);
                setStartTimeRoot.addChild(seconds);
            }

            {
                // RemoveCustomBoard command (SetCustomBoard is registered in LockoutClient, and server listens for a packet)
                dispatcher.getRoot().addChild(Commands.literal("RemoveCustomBoard").requires(PERMISSIONS).executes((context) -> {
                    ClientPlayNetworking.send(new CustomBoardPayload(Optional.empty()));
                    return 1;
                }).build());
            }

            {
                // SetBoardSize command
                var setBoardTimeRoot = Commands.literal("SetBoardSize").requires(PERMISSIONS).build();
                var size = Commands.argument("board size", IntegerArgumentType.integer(3, 7)).executes(LockoutServer::setBoardSize).build();

                dispatcher.getRoot().addChild(setBoardTimeRoot);
                setBoardTimeRoot.addChild(size);
            }

        });

        LootTableEvents.REPLACE.register(((key, original, source, registries) -> {
            if (Objects.equals(key, BuiltInLootTables.PIGLIN_BARTERING)) {
                var enchReg = registries.lookupOrThrow(Registries.ENCHANTMENT);
                var soulSpeed = enchReg.getOrThrow(Enchantments.SOUL_SPEED);

                UniformGenerator ironNuggetsCount = UniformGenerator.between(9.0F, 36.0F);
                UniformGenerator quartzCount = UniformGenerator.between(8.0F, 16.0F);
                UniformGenerator glowstoneDustCount = UniformGenerator.between(5.0F, 12.0F);
                UniformGenerator magmaCreamCount = UniformGenerator.between(2.0F, 6.0F);
                UniformGenerator enderPearlCount = UniformGenerator.between(4.0F, 8.0F);
                UniformGenerator stringCount = UniformGenerator.between(8.0F, 24.0F);
                UniformGenerator fireChargeCount = UniformGenerator.between(1.0F, 5.0F);
                UniformGenerator gravelCount = UniformGenerator.between(8.0F, 16.0F);
                UniformGenerator leatherCount = UniformGenerator.between(4.0F, 10.0F);
                UniformGenerator netherBrickCount = UniformGenerator.between(4.0F, 16.0F);
                UniformGenerator cryingObsidianCount = UniformGenerator.between(1.0F, 3.0F);
                UniformGenerator soulSandCount = UniformGenerator.between(4.0F, 16.0F);

                LootPool.Builder pool = LootPool.lootPool()
                        .add(LootItem.lootTableItem(Items.BOOK).setWeight(5).apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(soulSpeed)))
                        .add(LootItem.lootTableItem(Items.IRON_BOOTS).setWeight(8).apply(EnchantRandomlyFunction.randomEnchantment().withEnchantment(soulSpeed)))
                        .add(LootItem.lootTableItem(Items.POTION).setWeight(10).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)))
                        .add(LootItem.lootTableItem(Items.SPLASH_POTION).setWeight(10).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)))
                        .add(LootItem.lootTableItem(Items.IRON_NUGGET).setWeight(10).apply(SetItemCountFunction.setCount(ironNuggetsCount)))
                        .add(LootItem.lootTableItem(Items.QUARTZ).setWeight(20).apply(SetItemCountFunction.setCount(quartzCount)))
                        .add(LootItem.lootTableItem(Items.GLOWSTONE_DUST).setWeight(20).apply(SetItemCountFunction.setCount(glowstoneDustCount)))
                        .add(LootItem.lootTableItem(Items.MAGMA_CREAM).setWeight(20).apply(SetItemCountFunction.setCount(magmaCreamCount)))
                        .add(LootItem.lootTableItem(Items.ENDER_PEARL).setWeight(20).apply(SetItemCountFunction.setCount(enderPearlCount)))
                        .add(LootItem.lootTableItem(Items.STRING).setWeight(20).apply(SetItemCountFunction.setCount(stringCount)))
                        .add(LootItem.lootTableItem(Items.FIRE_CHARGE).setWeight(40).apply(SetItemCountFunction.setCount(fireChargeCount)))
                        .add(LootItem.lootTableItem(Items.GRAVEL).setWeight(40).apply(SetItemCountFunction.setCount(gravelCount)))
                        .add(LootItem.lootTableItem(Items.LEATHER).setWeight(40).apply(SetItemCountFunction.setCount(leatherCount)))
                        .add(LootItem.lootTableItem(Items.NETHER_BRICK).setWeight(40).apply(SetItemCountFunction.setCount(netherBrickCount)))
                        .add(LootItem.lootTableItem(Items.OBSIDIAN).setWeight(40))
                        .add(LootItem.lootTableItem(Items.CRYING_OBSIDIAN).setWeight(40).apply(SetItemCountFunction.setCount(cryingObsidianCount)))
                        .add(LootItem.lootTableItem(Items.SOUL_SAND).setWeight(40).apply(SetItemCountFunction.setCount(soulSandCount)));
                return LootTable.lootTable().withPool(pool).build();
            }
            return null;
        }));

    }

}
