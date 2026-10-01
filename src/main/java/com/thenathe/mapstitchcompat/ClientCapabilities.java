package com.thenathe.mapstitchcompat;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.impl.networking.CommonPacketsImpl;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.ChannelInfoHolder;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Uses channels advertised by an untouched MapStitch client through Fabric's common protocol. */
public final class ClientCapabilities {
    private static final Set<Identifier> MAPSTITCH_CHANNELS = Set.of(
            Identifier.parse("mapstitch:dimension_ids"),
            Identifier.parse("mapstitch:open_world_map_screen"),
            Identifier.parse("mapstitch:sync_world_map"),
            Identifier.parse("mapstitch:s2c_play_sound")
    );

    private ClientCapabilities() {
    }

    public static boolean hasMapstitch(Connection connection) {
        if (connection == null) {
            return false;
        }
        if (connection.getPacketListener() instanceof ServerGamePacketListenerImpl play) {
            return ServerPlayNetworking.getSendable(play.player).containsAll(MAPSTITCH_CHANNELS);
        }
        // Fabric stores the PLAY advertisement here during CONFIGURATION, then consumes it
        // when constructing its play networking addon. Do not cache a result across reconnects.
        return connection instanceof ChannelInfoHolder holder
                && hasMapstitchChannels(holder.fabric_getPendingChannelsNames(ConnectionProtocol.PLAY));
    }

    public static boolean hasMapstitch(ServerPlayer player) {
        return player != null && ServerPlayNetworking.getSendable(player).containsAll(MAPSTITCH_CHANNELS);
    }

    public static boolean hasMapstitch(ServerConfigurationPacketListenerImpl listener) {
        return hasMapstitch(((ServerCommonPacketListenerImplAccessor) listener).getConnection());
    }

    public static boolean hasMapstitchChannels(Collection<Identifier> channels) {
        return channels.containsAll(MAPSTITCH_CHANNELS);
    }

    public static void beforeRegistrySync(ServerConfigurationPacketListenerImpl listener, Runnable configure) {
        if (!ServerConfigurationNetworking.canSend(listener, CommonVersionPayload.TYPE)
                || !ServerConfigurationNetworking.canSend(listener, CommonRegisterPayload.TYPE)) {
            configure.run();
            return;
        }
        // Registry sync runs during BEFORE_CONFIGURE, whereas Fabric normally queries PLAY
        // channels during CONFIGURE. Reuse the standard protocol and exact task keys: Fabric's
        // existing receivers validate the replies, store the channels, and complete these tasks.
        // Vanilla clients are never queried. No extra receiver is needed on modded clients.
        listener.addTask(new CommonVersionTask(listener));
        listener.addTask(new CommonPlayChannelsTask(listener));
        listener.addTask(new DeferredRegistryTask(listener, configure));
    }

    private record CommonVersionTask(ServerConfigurationPacketListenerImpl listener)
            implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            ServerConfigurationNetworking.send(listener,
                    new CommonVersionPayload(CommonPacketsImpl.SUPPORTED_COMMON_PACKET_VERSIONS));
        }

        @Override
        public Type type() {
            return new Type(CommonVersionPayload.TYPE.id().toString());
        }
    }

    private record CommonPlayChannelsTask(ServerConfigurationPacketListenerImpl listener)
            implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            ServerConfigurationNetworking.send(listener,
                    new CommonRegisterPayload(ServerNetworkingImpl.getAddon(listener).getNegotiatedVersion(),
                            CommonRegisterPayload.PLAY_PROTOCOL, ServerPlayNetworking.getGlobalReceivers()));
        }

        @Override
        public Type type() {
            return new Type(CommonRegisterPayload.TYPE.id().toString());
        }
    }

    private record DeferredRegistryTask(ServerConfigurationPacketListenerImpl listener, Runnable configure)
            implements ConfigurationTask {
        private static final Type TYPE = new Type("mapstitchcompat:prepare_registry_sync");

        @Override
        public void start(Consumer<Packet<?>> sender) {
            // The original implementation appends its actual sync task to the existing queue.
            configure.run();
            listener.completeTask(TYPE);
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }
}
