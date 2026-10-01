package pouchqa;

import java.nio.file.*;
import java.util.*;
import me.pajic.mapstitch.component.ModDataComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import org.joml.Vector2i;

public class PouchServerQa implements ModInitializer {
    final Path control = Path.of(System.getProperty("pouch.qa.control"));
    String previous = "";
    net.minecraft.world.level.saveddata.maps.MapItemSavedData currentMap;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(control.resolve("command"))) return;
                String command = Files.readString(control.resolve("command")).trim();

                if (previous.equals(command)) return;
                var player = server.getPlayerList().getPlayers().getFirst();
                if (command.startsWith("refresh-")) {
                    if (player.containerMenu != player.inventoryMenu) throw new AssertionError("Pouch menu must remain closed");
                    var pos = player.blockPosition().offset(4, -1, 4);
                    boolean first = command.equals("refresh-0");
                    var block = first ? net.minecraft.world.level.block.Blocks.LAPIS_BLOCK : net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK;
                    for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) player.level().setBlock(pos.offset(dx,0,dz), block.defaultBlockState(), 3);
                    int pixel = pos.getX() - currentMap.centerX + 64 + (pos.getZ() - currentMap.centerZ + 64) * 128;
                    int color = first ? net.minecraft.world.level.material.MapColor.LAPIS.id : net.minecraft.world.level.material.MapColor.FIRE.id;
                    Files.writeString(control.resolve("ack"), command + " " + pixel + " " + color);
                    previous = command;
                    return;
                }
                player.closeContainer();
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                player.setPermanentlyInvulnerable(true);
                var map = MapItem.create(player.level(), player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
                var id = map.get(DataComponents.MAP_ID);
                var data = MapItem.getSavedData(map, player.level());
                map.set(ModDataComponents.MAP_CENTER, new Vector2i(data.centerX, data.centerZ));
                data.setColor(64, 64, (byte) 34);
                currentMap = data;
                var atlas = new ItemStack(me.pajic.mapstitch.item.ModItems.ATLAS);
                atlas.set(ModDataComponents.ATLAS_SCALE, 0);
                atlas.set(ModDataComponents.ATLAS_FULLNESS, 1);
                atlas.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(map))));
                boolean leggings = command.equals("leggings");
                var pouch = new ItemStack(leggings ? Items.IRON_LEGGINGS : me.pajic.toolpouch.item.ModItems.TOOL_POUCH);
                pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(atlas, new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK))));
                if (leggings) player.setItemSlot(EquipmentSlot.LEGS, pouch);
                else player.getInventory().setItem(0, pouch);
                player.getInventory().setSelectedSlot(0);
                player.inventoryMenu.broadcastFullState();
                Files.writeString(control.resolve("ack"), command + " " + id.id());
                previous = command;
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(control.resolve("result.txt"), "FAIL server " + failure); } catch (Exception ignored) {}
            }
        });
    }
}
