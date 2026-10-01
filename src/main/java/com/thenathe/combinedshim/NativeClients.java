package com.thenathe.combinedshim;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.impl.networking.ChannelInfoHolder;
import net.fabricmc.fabric.impl.networking.CommonPacketsImpl;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** One connection-local capability decision, independent of optional original mod classes. */
public final class NativeClients {
    private static final PacketContext.Key<Set<String>> MODULES = PacketContext.key(
            Identifier.parse("combined_polymer_shim:native_modules"));
    private static final PacketContext.Key<Boolean> QUERY_PENDING = PacketContext.key(
            Identifier.parse("combined_polymer_shim:querying_play_channels"));
    private static final Set<Identifier> TOOLPOUCH_CHANNELS = Set.of(
            Identifier.parse("toolpouch:s2c_sync_shulker_slot"),
            Identifier.parse("toolpouch:s2c_sync_arrow_slot"));
    private static final Set<Identifier> MAPSTITCH_CHANNELS = Set.of(
            Identifier.parse("mapstitch:dimension_ids"),
            Identifier.parse("mapstitch:open_world_map_screen"),
            Identifier.parse("mapstitch:sync_world_map"),
            Identifier.parse("mapstitch:s2c_play_sound"));

    private NativeClients() {}

    public static boolean isNative(PacketContext context, String modId) {
        var modules = context == null ? null : context.get(MODULES);
        return modules != null && modules.contains(modId);
    }

    public static boolean isNativeEntry(PacketContext context, Identifier id) {
        return id != null && isNative(context, id.getNamespace());
    }

    public static boolean hasRegistryReceiver(ServerConfigurationPacketListenerImpl listener) {
        return ServerConfigurationNetworking.canSend(listener, Identifier.parse("fabric:registry/sync"));
    }

    public static void classify(ServerConfigurationPacketListenerImpl listener) {
        var selected = new HashSet<String>();
        if (hasRegistryReceiver(listener)) {
            // These originals expose no unique PLAY capability: retain strict native validation.
            if (loaded("tiered_backpacks")) selected.add("tiered_backpacks");
            if (loaded("simple_smithing_overhaul")) selected.add("simple_smithing_overhaul");
            var connection = ((ServerCommonPacketListenerImplAccessor) listener).getConnection();
            if (connection instanceof ChannelInfoHolder holder) {
                var channels = holder.fabric_getPendingChannelsNames(ConnectionProtocol.PLAY);
                if (loaded("toolpouch") && channels.containsAll(TOOLPOUCH_CHANNELS)) selected.add("toolpouch");
                if (loaded("mapstitch") && channels.containsAll(MAPSTITCH_CHANNELS)) selected.add("mapstitch");
            }
        }
        listener.getPacketContext().set(MODULES, Set.copyOf(selected));
    }

    private static boolean loaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    /** Reuse Fabric's existing common protocol once, before native registry selection. */
    public static void beforeRegistrySync(ServerConfigurationPacketListenerImpl listener, Runnable configure) {
        var context = listener.getPacketContext();
        if ((!loaded("toolpouch") && !loaded("mapstitch"))
                || Boolean.TRUE.equals(context.get(QUERY_PENDING))
                || !ServerConfigurationNetworking.canSend(listener, CommonVersionPayload.TYPE)
                || !ServerConfigurationNetworking.canSend(listener, CommonRegisterPayload.TYPE)) {
            configure.run();
            return;
        }
        context.set(QUERY_PENDING, true);
        listener.addTask(new CommonVersionTask(listener));
        listener.addTask(new CommonPlayChannelsTask(listener));
        listener.addTask(new DeferredRegistryTask(listener, configure));
    }

    private record CommonVersionTask(ServerConfigurationPacketListenerImpl listener) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> sender) {
            ServerConfigurationNetworking.send(listener, new CommonVersionPayload(CommonPacketsImpl.SUPPORTED_COMMON_PACKET_VERSIONS));
        }
        @Override public Type type() { return new Type(CommonVersionPayload.TYPE.id().toString()); }
    }

    private record CommonPlayChannelsTask(ServerConfigurationPacketListenerImpl listener) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> sender) {
            ServerConfigurationNetworking.send(listener,
                    new CommonRegisterPayload(ServerNetworkingImpl.getAddon(listener).getNegotiatedVersion(),
                            CommonRegisterPayload.PLAY_PROTOCOL, ServerPlayNetworking.getGlobalReceivers()));
        }
        @Override public Type type() { return new Type(CommonRegisterPayload.TYPE.id().toString()); }
    }

    private record DeferredRegistryTask(ServerConfigurationPacketListenerImpl listener, Runnable configure) implements ConfigurationTask {
        private static final Type TYPE = new Type("combined_polymer_shim:prepare_registry_sync");
        @Override public void start(Consumer<Packet<?>> sender) {
            configure.run();
            // Configuration can repeat on the same connection; query fresh PLAY capabilities then.
            listener.getPacketContext().set(QUERY_PENDING, false);
            listener.completeTask(TYPE);
        }
        @Override public Type type() { return TYPE; }
    }
}
