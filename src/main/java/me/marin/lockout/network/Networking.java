package me.marin.lockout.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class Networking {
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(UpdateTimerPayload.ID, UpdateTimerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpdateTooltipPayload.ID, UpdateTooltipPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LockoutGoalsTeamsPayload.ID, LockoutGoalsTeamsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StartLockoutPayload.ID, StartLockoutPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompleteTaskPayload.ID, CompleteTaskPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EndLockoutPayload.ID, EndLockoutPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LockoutVersionPayload.ID, LockoutVersionPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(CustomBoardPayload.ID, CustomBoardPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LockoutVersionPayload.ID, LockoutVersionPayload.CODEC);
    }
}
