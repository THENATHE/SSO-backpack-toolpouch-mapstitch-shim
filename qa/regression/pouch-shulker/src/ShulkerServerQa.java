package shulkerqa;

import java.nio.file.*;
import java.util.*;
import me.pajic.toolpouch.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

public class ShulkerServerQa implements ModInitializer {
    final Path control = Path.of(System.getProperty("shulker.qa.control"));
    String previous = "";
    int lastMenu = -1;
    boolean boundariesDone;
    ItemStack named(Item item, int count, String name, int damage) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (damage > 0) stack.setDamageValue(damage);
        return stack;
    }
    ItemStack pouch(boolean second) {
        ItemStack box = named(Items.SHULKER_BOX, 1, second ? "QA-box-B" : "QA-box-A", 0);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(second
            ? List.of(named(Items.DIAMOND_AXE, 1, "QA-B-axe", 91), named(Items.GOLD_INGOT, 5, "QA-B-gold", 0))
            : List.of(named(Items.DIAMOND_SWORD, 1, "QA-A-sword", 37), named(Items.DIAMOND, 7, "QA-A-diamonds", 0), named(Items.BOW, 1, "QA-A-bow", 84))));
        ItemStack pouch = named(ModItems.TOOL_POUCH, 1, second ? "QA-pouch-B" : "QA-pouch-A", 0);
        ItemStack extra = named(Items.SHULKER_BOX, 1, "QA-box-C", 0);
        extra.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(named(Items.DIAMOND_HOE, 1, "QA-C-hoe", 12), named(Items.EMERALD, 3, "QA-C-emeralds", 0))));
        pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(second ? List.of(box) : List.of(box, extra)));
        return pouch;
    }
    void count(ItemStack stack, Map<String,Integer> result, int depth) {
        if (stack.isEmpty()) return;
        if (depth > 16) throw new AssertionError("unexpected container depth");
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getString().startsWith("QA-")) {
            String key = name.getString() + "@damage=" + stack.getDamageValue();
            result.merge(key, stack.getCount(), Integer::sum);
        }
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().forEach(child -> count(child, result, depth + 1));
    }
    String snapshot(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) throw new AssertionError("authoritative accounting requires child menu closed");
        Map<String,Integer> counts = new TreeMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) count(player.getInventory().getItem(i), counts, 0);
        count(player.inventoryMenu.getCarried(), counts, 0);
        for (var entity : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64))) count(entity.getItem(), counts, 0);
        return counts.toString();
    }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (server.getPlayerList().getPlayers().isEmpty()) return;
                if (!boundariesDone && Boolean.getBoolean("shulker.qa.boundaries")) {
                    boundariesDone = true;
                    var method = Class.forName("shulkerqa.ShulkerServerBoundaryChecks").getMethod("run", net.minecraft.server.MinecraftServer.class);
                    @SuppressWarnings("unchecked") var results = (List<String>) method.invoke(null, server);
                    Files.write(control.resolve("boundary-results.txt"),results);
                }
                var player = server.getPlayerList().getPlayers().getFirst();
                if (lastMenu != player.containerMenu.containerId) {
                    lastMenu = player.containerMenu.containerId;
                    Files.writeString(control.resolve("menu-trace.txt"), "menu=" + lastMenu + " class=" + player.containerMenu.getClass().getSimpleName() + " command=" + previous + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                // Keep deliberately thrown owner pouches available for authoritative accounting.
                for (var item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64))) item.setNeverPickUp();
                if (!Files.exists(control.resolve("command"))) return;
                String command = Files.readString(control.resolve("command")).trim();
                if (command.equals(previous)) return;
                previous = command;
                if (command.startsWith("seed ")) {
                    player.closeContainer();
                    player.getInventory().clearContent();
                    player.inventoryMenu.setCarried(ItemStack.EMPTY);
                    for (var item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(64))) item.discard();
                    player.setGameMode(GameType.SURVIVAL);
                    player.setPermanentlyInvulnerable(true);
                    player.getInventory().setItem(0, pouch(false));
                    player.getInventory().setItem(1, pouch(true));
                    player.getInventory().setItem(2, named(Items.DIAMOND_PICKAXE, 1, "QA-deposit-pickaxe", 15));
                    if (command.contains("leggings-beds")) {
                        var box = named(Items.SHULKER_BOX, 1, "QA-bed-box", 0);
                        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(named(Items.BED.white(),1,"QA-bed-1",0),named(Items.BED.red(),1,"QA-bed-2",0),named(Items.BED.blue(),1,"QA-bed-3",0),named(Items.DIAMOND,7,"QA-bed-diamonds",0))));
                        var leggings = named(Items.IRON_LEGGINGS,1,"QA-bed-leggings",23);
                        leggings.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(box)));
                        player.getInventory().setItem(0,ItemStack.EMPTY);
                        player.getInventory().setItem(1,ItemStack.EMPTY);
                        player.setItemSlot(EquipmentSlot.LEGS,leggings);
                    }
                    player.getInventory().setSelectedSlot(0);
                    player.inventoryMenu.broadcastFullState();
                    Files.writeString(control.resolve("expected.txt"), snapshot(player));
                }
                String state = player.containerMenu == player.inventoryMenu ? snapshot(player) : "OPEN:" + player.containerMenu.getClass().getName();
                Files.writeString(control.resolve("ack"), command + "\n" + state);
                Files.writeString(control.resolve("server-snapshots.txt"), command + "\n" + state + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(control.resolve("result.txt"), "ERROR server " + failure); } catch (Exception ignored) {}
            }
        });
    }
}
