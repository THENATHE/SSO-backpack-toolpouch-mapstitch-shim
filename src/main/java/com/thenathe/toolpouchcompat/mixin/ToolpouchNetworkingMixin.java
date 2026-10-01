package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.ToolpouchCompat;
import me.pajic.toolpouch.platform.fabric.FabricLoaderUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricLoaderUtil.class, remap = false)
public abstract class ToolpouchNetworkingMixin {
    @Inject(method = "sendToClient", at = @At("HEAD"), cancellable = true)
    private void toolpouchcompat$supportedOnly(ServerPlayer player, CustomPacketPayload payload, CallbackInfo ci) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) ci.cancel();
    }
    @Inject(method = "openToolPouchScreen", at = @At("HEAD"), cancellable = true)
    private void toolpouchcompat$guardMenu(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && !ToolpouchCompat.nativeClient(serverPlayer.connection.getPacketContext())) {
            ToolpouchCompat.notice(serverPlayer);
            ci.cancel();
        }
    }
}
