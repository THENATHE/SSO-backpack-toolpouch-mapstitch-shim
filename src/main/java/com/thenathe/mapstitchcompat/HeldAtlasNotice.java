package com.thenathe.mapstitchcompat;

import me.pajic.mapstitch.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** An action-bar hint on pickup/equip, repeated every ten seconds while held. */
public final class HeldAtlasNotice {
    private static final Component MESSAGE = Component.literal("Install MapStitch on your client to use this atlas.");
    private static final Map<UUID, Integer> NEXT_NOTICE = new HashMap<>();
    private static final int INTERVAL = 200;

    private HeldAtlasNotice() {}

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT_NOTICE.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTickCount();
            for (var player : server.getPlayerList().getPlayers()) {
                var id = player.getUUID();
                boolean needsHint = !MapstitchCompat.nativeClient(player.connection.getPacketContext())
                        && (player.getMainHandItem().is(ModItems.ATLAS) || player.getOffhandItem().is(ModItems.ATLAS));
                if (!needsHint) {
                    NEXT_NOTICE.remove(id);
                } else if (!NEXT_NOTICE.containsKey(id) || now - NEXT_NOTICE.get(id) >= 0) {
                    player.sendOverlayMessage(MESSAGE);
                    NEXT_NOTICE.put(id, now + INTERVAL);
                }
            }
        });
    }
}
