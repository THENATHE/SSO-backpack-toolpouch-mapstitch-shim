package com.thenathe.ssopolymer.mixin;

import com.thenathe.combinedshim.WireRegistries;
import com.thenathe.ssopolymer.DefaultedPacketProjection;
import net.atlas.defaulted.networking.ClientboundDefaultComponentsSyncPacket;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Optional developer-Defaulted integration; the port track does not load this mixin. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class DefaultedSyncMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> ssoPolymer$projectDefaultedTargets(Packet<?> packet) {
        var context = ((PacketContextProvider) this).getPacketContext();
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof ClientboundDefaultComponentsSyncPacket defaults)
                || !WireRegistries.hasMapping(context)) return packet;
        var projected = DefaultedPacketProjection.project(defaults,
                holder -> WireRegistries.isVisible(BuiltInRegistries.ITEM, holder.value(), context));
        return projected == defaults ? packet : new ClientboundCustomPayloadPacket(projected);
    }
}
