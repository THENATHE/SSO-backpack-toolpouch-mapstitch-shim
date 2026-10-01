package com.thenathe.mapstitchcompat.mixin;

import me.pajic.mapstitch.platform.fabric.FabricLoaderUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricLoaderUtil.class, remap = false)
public abstract class MapstitchNetworkingMixin {
    @Inject(method = "s2c", at = @At("HEAD"), cancellable = true)
    private void mapstitchcompat$sendOnlySupported(ServerPlayer player, CustomPacketPayload payload, CallbackInfo ci) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) ci.cancel();
    }
}
