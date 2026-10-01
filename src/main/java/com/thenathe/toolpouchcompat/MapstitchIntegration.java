package com.thenathe.toolpouchcompat;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;

/** Keeps optional MapStitch classes out of the standalone shim's initialization path. */
public final class MapstitchIntegration {
    private MapstitchIntegration() {}

    public static boolean nativeClient(PacketContext context) {
        return FabricLoader.getInstance().isModLoaded("mapstitch")
                && Installed.nativeClient(context);
    }

    private static final class Installed {
        static boolean nativeClient(PacketContext context) {
            return com.thenathe.mapstitchcompat.MapstitchCompat.nativeClient(context);
        }
    }
}
