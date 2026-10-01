package com.thenathe.combinedshim.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thenathe.combinedshim.WireRegistries;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketEncoder.class)
public abstract class RegistryPacketEncoderMixin {
    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void combinedshim$encode(ChannelHandlerContext channel, Packet<?> packet, ByteBuf output, Operation<Void> original) {
        // Polymer resolves tag integers during filtering; those are already translated.
        WireRegistries.encode(packet instanceof ClientboundUpdateTagsPacket, () -> original.call(channel, packet, output));
    }
}
