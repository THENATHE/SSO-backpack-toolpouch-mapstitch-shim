package com.thenathe.combinedshim.mixin;

import com.thenathe.combinedshim.NativeClients;
import com.thenathe.combinedshim.WireRegistries;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.LinkedHashMap;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class NativeTagsMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> combinedshim$tagIds(Packet<?> packet) {
        var context = ((PacketContextProvider) this).getPacketContext();
        if (!(packet instanceof ClientboundUpdateTagsPacket tags) || !WireRegistries.hasMapping(context)) return packet;
        var result = new LinkedHashMap<>(tags.tags());
        result.replaceAll((registry, payload) -> {
            if (!WireRegistries.handles(registry.identifier())) return payload;
            var translated = new LinkedHashMap<Identifier, IntList>();
            payload.tags().forEach((tag, rawIds) -> {
                var wireIds = new IntArrayList();
                for (int raw : rawIds) {
                    int wire = WireRegistries.toWire(registry.identifier(), raw, context);
                    if (wire >= 0) wireIds.add(wire);
                }
                translated.put(tag, wireIds);
            });
            return new TagNetworkSerialization.NetworkPayload(translated);
        });
        return new ClientboundUpdateTagsPacket(result);
    }
}
