package com.thenathe.combinedshim.mixin;

import com.thenathe.combinedshim.NativeClients;
import com.thenathe.combinedshim.WireRegistries;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = RegistrySyncManager.class, remap = false)
public abstract class NativeRegistrySyncMixin {
    @ModifyVariable(method = "configureClient", at = @At("STORE"), ordinal = 0)
    private static Map<Identifier, Object2IntMap<Identifier>> combinedshim$restoreNative(
            Map<Identifier, Object2IntMap<Identifier>> original,
            ServerConfigurationPacketListenerImpl listener, MinecraftServer server) {
        NativeClients.classify(listener);
        if (!NativeClients.hasRegistryReceiver(listener)) return original;
        var result = new LinkedHashMap<Identifier, Object2IntMap<Identifier>>();
        if (original != null) original.forEach((key, entries) -> result.put(key, new Object2IntLinkedOpenHashMap<>(entries)));
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            if (!WireRegistries.handles(registry.key().identifier())) continue;
            var entries = result.computeIfAbsent(registry.key().identifier(), ignored -> new Object2IntLinkedOpenHashMap<>());
            combinedshim$restore(registry, entries, listener.getPacketContext());
        }
        return result;
    }

    /** Every restoration completes before the single final map is assigned. */
    @ModifyArg(method = "configureClient", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/fabric/impl/registry/sync/RegistrySyncManager$SyncConfigurationTask;<init>(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Ljava/util/Map;)V"), index = 1)
    private static Map<Identifier, Object2IntMap<Identifier>> combinedshim$finalWireMap(
            ServerConfigurationPacketListenerImpl listener, Map<Identifier, Object2IntMap<Identifier>> map) {
        return WireRegistries.prepare(map, listener.getPacketContext());
    }

    @Unique private static <T> void combinedshim$restore(Registry<T> registry,
            Object2IntMap<Identifier> entries, PacketContext context) {
        for (T value : registry) {
            var id = registry.getKey(value);
            if (id != null && (id.getNamespace().equals("minecraft") || NativeClients.isNativeEntry(context, id))) {
                entries.put(id, registry.getId(value));
            }
        }
    }
}
