package elytraqa;

import java.nio.file.*;
import java.util.List;
import java.util.Set;
import com.thenathe.toolpouchcompat.ElytraPreference;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public class ElytraServerQa implements ModInitializer {
    final Path control = Path.of(System.getProperty("elytra.qa.control"));
    String previous = "";

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(control.resolve("command"))) return;
                String command = Files.readString(control.resolve("command")).trim();
                if (previous.equals(command)) return;
                var player = server.getPlayerList().getPlayers().getFirst();
                if (command.startsWith("seed-")) {
                    player.closeContainer();
                    player.getInventory().clearContent();
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    player.setPermanentlyInvulnerable(true);
                    boolean leggings = command.contains("leggings");
                    var pouch = new ItemStack(leggings ? Items.IRON_LEGGINGS : me.pajic.toolpouch.item.ModItems.TOOL_POUCH);
                    pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.ELYTRA), new ItemStack(Items.FIREWORK_ROCKET, 16))));
                    if (leggings) player.setItemSlot(EquipmentSlot.LEGS, pouch);
                    else player.getInventory().setItem(0, pouch);
                    player.getInventory().setSelectedSlot(0);
                    player.inventoryMenu.broadcastFullState();
                } else if (command.equals("chest")) {
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                    player.inventoryMenu.broadcastFullState();
                } else if (command.equals("respawn")) {
                    player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                } else if (command.equals("change-dimension")) {
                    player.teleportTo(server.getLevel(Level.NETHER), 0.5, 100, 0.5, Set.of(), 0, 0, true);
                }
                var canGlide = LivingEntity.class.getDeclaredMethod("canGlide");
                canGlide.setAccessible(true);
                boolean enabled = ((ElytraPreference) player).toolpouchCompat$elytraEnabled();
                // Hold all other vanilla flight preconditions equal while querying eligibility.
                boolean grounded = player.onGround();
                player.setOnGround(false);
                boolean glide;
                try { glide = (boolean) canGlide.invoke(player); }
                finally { player.setOnGround(grounded); }
                boolean cosmetic = ToolPouchUtil.getElytraFromToolPouch(player, true) != null;
                Files.writeString(control.resolve("ack"), command + " " + enabled + " " + glide + " " + cosmetic + " " + player.getId());
                previous = command;
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(control.resolve("result.txt"), "FAIL server " + failure); } catch (Exception ignored) {}
            }
        });
    }
}
