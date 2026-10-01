package com.thenathe.combinedshim.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thenathe.combinedshim.NativeClients;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/** The only early PLAY-channel query scheduler in the consolidated shim. */
@Mixin(value = RegistrySyncManager.class, remap = false)
public abstract class RegistrySyncSchedulingMixin {
    @WrapMethod(method = "configureClient")
    private static void combinedshim$queryChannels(ServerConfigurationPacketListenerImpl listener,
            MinecraftServer server, Operation<Void> original) {
        NativeClients.beforeRegistrySync(listener, () -> original.call(listener, server));
    }
}
