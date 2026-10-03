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
    int checks;
    net.minecraft.world.phys.Vec3 returnPosition;
    void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    void regressions(net.minecraft.server.level.ServerPlayer player) throws Exception {
        var level = player.level();
        var map = MapItem.create(level, player.getBlockX(), player.getBlockZ(), (byte) 0, true, false);
        var id = map.get(DataComponents.MAP_ID);
        map.setCount(16);
        var recipe = new me.pajic.mapstitch.recipe.AtlasRecipe();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), map));
        check(recipe.matches(input, level), "valid atlas recipe matches");
        var atlas = recipe.assemble(input);
        var seed = atlas.get(DataComponents.BUNDLE_CONTENTS).items().getFirst();
        check(seed.count() == 1, "stacked map ingredient consumes only one map, not sixteen");
        check(seed.get(ModDataComponents.MAP_CENTER) != null, "recipe seed receives map center before first tick");
        check(map.getCount() == 16 && !map.has(ModDataComponents.MAP_CENTER), "recipe never mutates crafting ingredient");
        check(ItemStack.isSameItemSameComponents(atlas, recipe.assemble(input)), "repeated recipe preview preserves seed map");
        var invalid = new ItemStack(Items.FILLED_MAP);
        invalid.set(DataComponents.MAP_ID, new net.minecraft.world.level.saveddata.maps.MapId(Integer.MAX_VALUE));
        var invalidInput = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), invalid));
        check(!recipe.matches(invalidInput, level), "unknown map cannot inherit previous recipe scale");
        check(recipe.assemble(invalidInput).isEmpty(), "unknown map cannot craft phantom atlas");
        check(recipe.matches(input, level) && !recipe.assemble(input).isEmpty(), "valid craft recovers after invalid input");
        var otherMap = MapItem.create(level, player.getBlockX() + 2048, player.getBlockZ(), (byte) 2, true, false);
        var otherInput = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), otherMap));
        check(recipe.matches(otherInput, level), "different map matches at different scale");
        var assembledCurrent = recipe.assemble(input);
        check(assembledCurrent.get(ModDataComponents.ATLAS_SCALE) == 0 && assembledCurrent.get(DataComponents.BUNDLE_CONTENTS).items().getFirst().get(DataComponents.MAP_ID).equals(id), "assembly uses actual input after another recipe preview");
        // Exercise stale active IDs and both metadata variants in actual inventoryTick.
        atlas.set(ModDataComponents.ATLAS_ACTIVE_MAP_ID, Integer.MAX_VALUE);
        var oldMap = map.copyWithCount(1);
        oldMap.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Seed map metadata survives"));
        atlas.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(oldMap))));
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        var repaired = atlas.get(DataComponents.BUNDLE_CONTENTS).items().getFirst();
        check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == id.id(), "stale active ID recovers to contained map");
        check(repaired.get(ModDataComponents.MAP_CENTER) != null, "old atlas seed metadata repaired");
        check(repaired.get(DataComponents.CUSTOM_NAME).equals(oldMap.get(DataComponents.CUSTOM_NAME)), "repair preserves custom map name");
        atlas.set(ModDataComponents.ATLAS_ACTIVE_MAP_ID, otherMap.get(DataComponents.MAP_ID).id());
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == id.id(), "existing but uncontained active map is replaced");
        oldMap.set(ModDataComponents.MAP_CENTER, new Vector2i(Integer.MAX_VALUE, Integer.MIN_VALUE));
        atlas.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(oldMap))));
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        var actual = level.getMapData(id);
        check(atlas.get(DataComponents.BUNDLE_CONTENTS).items().getFirst().get(ModDataComponents.MAP_CENTER).equals(new Vector2i(actual.centerX, actual.centerZ)), "incorrect map center repaired from authoritative saved data");
        var largeContents = new BundleContents(List.of(
            ItemStackTemplate.fromNonEmptyStack(oldMap.copyWithCount(16)),
            ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.MAP, 64)),
            ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.PAPER, 64)))).asMutable();
        largeContents.toggleSelectedItem(1);
        atlas.set(DataComponents.BUNDLE_CONTENTS, largeContents.toImmutable());
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        var preserved = atlas.get(DataComponents.BUNDLE_CONTENTS);
        check(preserved.items().stream().mapToInt(ItemStackTemplate::count).sum() == 144, "metadata repair retains atlas contents beyond vanilla bundle capacity");
        check(preserved.size() == 3 && preserved.items().get(0).count() == 16 && preserved.items().get(1).is(Items.MAP) && preserved.items().get(2).is(Items.PAPER), "metadata repair preserves counts and ordering");
        check(preserved.getSelectedItemIndex() == 1, "metadata repair preserves selected bundle item");
        atlas.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == -1, "empty atlas does not retain removed active map");
        atlas.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(invalid))));
        ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
        check(atlas.get(DataComponents.BUNDLE_CONTENTS).items().getFirst().get(DataComponents.MAP_ID).equals(invalid.get(DataComponents.MAP_ID)), "missing saved map data is preserved, not discarded");
        double originalX = player.getX(), originalY = player.getY(), originalZ = player.getZ();
        for (byte scale = 0; scale <= 4; scale++) {
            var scaled = MapItem.create(level, (int)originalX, (int)originalZ, scale, true, false);
            var scaledId = scaled.get(DataComponents.MAP_ID);
            var scaledData = level.getMapData(scaledId);
            level.setMapData(scaledId, scaledData.locked()); // skip terrain generation in this selection check
            atlas.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(scaled))));
            atlas.set(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1);
            player.setPos(scaledData.centerX, originalY, scaledData.centerZ);
            ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
            check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == scaledId.id(), "scale " + scale + " selects center");
            player.setPos(scaledData.centerX + (63 << scale), originalY, scaledData.centerZ);
            ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
            check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == scaledId.id(), "scale " + scale + " retains map inside edge");
            player.setPos(scaledData.centerX + (65 << scale), originalY, scaledData.centerZ);
            ((me.pajic.mapstitch.item.AtlasItem) atlas.getItem()).inventoryTick(atlas, level, player, null);
            check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == -1, "scale " + scale + " clears map outside edge");
        }
        player.setPos(originalX, originalY, originalZ);
        Files.writeString(control.resolve("ack"), "regressions PASS " + checks + " server assertions: recipe count, repeated assembly, seed metadata, invalid map, stale IDs, metadata/name preservation");
    }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(control.resolve("command"))) return;
                String command = Files.readString(control.resolve("command")).trim();

                if (previous.equals(command)) return;
                var player = server.getPlayerList().getPlayers().getFirst();
                if (command.equals("craft-setup")) {
                    player.closeContainer();
                    player.getInventory().clearContent();
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    var seed = MapItem.create(player.level(), player.getBlockX(), player.getBlockZ(), (byte)0, true, false);
                    seed.setCount(16);
                    player.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.BOOK, 8));
                    player.inventoryMenu.getCraftSlots().setItem(1, seed);
                    player.inventoryMenu.slotsChanged(player.inventoryMenu.getCraftSlots());
                    player.inventoryMenu.broadcastFullState();
                    previous = command; return;
                }
                if (command.equals("craft-check")) {
                    var carried = player.inventoryMenu.getCarried();
                    check(carried.is(me.pajic.mapstitch.item.ModItems.ATLAS), "server accepts native client crafting click");
                    check(carried.get(DataComponents.BUNDLE_CONTENTS).items().getFirst().count() == 1, "server crafted atlas contains exactly one seed");
                    check(player.inventoryMenu.getSlot(1).getItem().getCount() == 7 && player.inventoryMenu.getSlot(2).getItem().getCount() == 15, "server consumed exactly one book and one map");
                    Files.writeString(control.resolve("ack"), "craft PASS native client click + authoritative server inventory; total " + checks + " server assertions");
                    previous = command; return;
                }
                if (command.equals("world-nether")) {
                    returnPosition = player.position();
                    player.teleportTo(server.getLevel(net.minecraft.world.level.Level.NETHER), 0.5, 100, 0.5, Set.of(), 0, 0, true);
                    previous = command; return;
                }
                if (command.equals("world-overworld")) {
                    player.teleportTo(server.overworld(), returnPosition.x, returnPosition.y, returnPosition.z, Set.of(), 0, 0, true);
                    previous = command; return;
                }
                if (command.equals("regressions")) { regressions(player); previous = command; return; }
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
                // Deliberately missing: seed maps crafted before a direct inventory sync.
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
