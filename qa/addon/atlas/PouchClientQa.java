package pouchqa;

import java.nio.file.*;
import java.util.*;
import me.pajic.mapstitch.MapStitch;
import me.pajic.mapstitch.component.ModDataComponents;
import me.pajic.mapstitch.item.ModItems;
import me.pajic.mapstitch.minimap.MinimapOverlay;
import me.pajic.mapstitch.util.ModClientUtil;
import me.pajic.mapstitch.worldmap.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

public class PouchClientQa implements ClientModInitializer {
    final Path control = Path.of(System.getProperty("pouch.qa.control"));
    final List<String> evidence = new ArrayList<>();
    int phase, ticks, scenario, checks;
    boolean done;
    MapId id;
    final MapId staleWorldId = new MapId(Integer.MAX_VALUE - 1);
    net.minecraft.client.multiplayer.ClientLevel renderedLevel;
    void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    Object field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    void scans(String location) {
        var requirements = MapStitch.CONFIG.itemRequirements;
        requirements.minimapAtlasScan.trySetQuiet(List.of(location));
        requirements.worldMapAtlasScan.trySetQuiet(List.of(location));
        requirements.compassAndClockScan.trySetQuiet(List.of(location));
    }
    public void onInitializeClient() {
        // Observe an actual HUD frame after MapStitch, not just a dimension-change tick.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MOB_EFFECTS,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("pouchqa", "minimap-observer"),
            (graphics, counter) -> renderedLevel = net.minecraft.client.Minecraft.getInstance().level);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.player == null || client.level == null) return;
            try {
                if (++ticks > 900) throw new AssertionError("timeout phase=" + phase);
                switch (phase) {
                    case 0 -> {
                        if (ticks < 60) return;
                        if (Boolean.getBoolean("pouch.qa.regressionsOnly")) {
                            Files.writeString(control.resolve("command"), "regressions"); phase = 4; ticks = 0; return;
                        }
                        scans("accessories");
                        ((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).clear();
                        WorldMapScreen.clearMaps();
                        Files.writeString(control.resolve("command"), scenario == 0 ? "inventory" : "leggings");
                        phase = 1; ticks = 0;
                    }
                    case 1 -> {
                        if (ticks < 100 || !Files.exists(control.resolve("ack"))) return;
                        var ack = Files.readString(control.resolve("ack")).split(" ");
                        if (!ack[0].equals(scenario == 0 ? "inventory" : "leggings")) return;
                        id = new MapId(Integer.parseInt(ack[1]));
                        check(client.level.getMapData(id) != null, "pouch map packet received");
                        var atlas = ModClientUtil.getFirstItem(client, ModItems.ATLAS);
                        check(atlas.is(ModItems.ATLAS), "accessories scan discovers pouch atlas");
                        check(atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == id.id(), "active map component synchronized");
                        check(ModClientUtil.hasCompass(client, "minimap"), "pouch compass detected");
                        check(ModClientUtil.hasClock(client, "time"), "pouch clock detected");
                        check(((ItemStack) field(MinimapOverlay.class, "lastAtlas")).getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1) == id.id(), "real minimap render selected atlas");
                        check(((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).get(id) != null, "real minimap render has non-null center for initially metadata-less seed map");
                        scans("hotbar");
                        check(ModClientUtil.getFirstItem(client, ModItems.ATLAS).isEmpty(), "disabled accessories excludes nested atlas");
                        check(!ModClientUtil.hasCompass(client, "minimap"), "disabled accessories excludes nested compass");
                        check(!ModClientUtil.hasClock(client, "time"), "disabled accessories excludes nested clock");
                        scans("accessories");
                        check(client.player.containerMenu == client.player.inventoryMenu, "pouch remains closed before map update");
                        Files.writeString(control.resolve("command"), "refresh-" + scenario);
                        phase = 3; ticks = 0;
                    }
                    case 3 -> {
                        if (ticks < 60 || !Files.exists(control.resolve("ack"))) return;
                        var response = Files.readString(control.resolve("ack")).split(" ");
                        if (!response[0].equals("refresh-" + scenario)) return;
                        int pixel = Integer.parseInt(response[1]), color = Integer.parseInt(response[2]);
                        if (Byte.toUnsignedInt(client.level.getMapData(id).colors[pixel]) / 4 != color) return;
                        check(client.player.containerMenu == client.player.inventoryMenu, "closed pouch received subsequent map delta");
                        check(Byte.toUnsignedInt(client.level.getMapData(id).colors[pixel]) / 4 == color, "terrain change mapped and synchronized while atlas nested in closed pouch");
                        client.setScreenAndShow(new WorldMapScreen(-1));
                        phase = 2; ticks = 0;
                    }
                    case 2 -> {
                        if (ticks < 60) return;
                        check(client.gui.screen() instanceof WorldMapScreen, "worldmap screen opened");
                        int maps = ((Map<?, ?>) field(WorldMapScreen.class, "MAPS")).size();
                        check(maps > 0, "real worldmap render discovered pouch source");
                        evidence.add((scenario == 0 ? "inventory" : "leggings") + ": map=" + id.id() + "; minimap rendered; compass and clock detected; disabling accessories excludes all three; worldmap tiles=" + maps);
                        client.gui.screen().onClose();
                        if (scenario++ == 0) { phase = 0; ticks = 0; }
                        else { Files.writeString(control.resolve("command"), "regressions"); phase = 4; ticks = 0; }
                    }
                    case 4 -> {
                        if (!Files.exists(control.resolve("ack"))) return;
                        String response = Files.readString(control.resolve("ack"));
                        if (!response.startsWith("regressions PASS")) return;
                        evidence.add(response);
                        if (Boolean.getBoolean("pouch.qa.regressionsOnly")) { finish(); return; }
                        check(((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).get(id) != null, "map center cached before dimension change");
                        @SuppressWarnings("unchecked") var cached = (Map<MapId, org.joml.Vector2i>) field(MinimapOverlay.class, "CACHED_CENTERS");
                        cached.put(staleWorldId, new org.joml.Vector2i(123456, -654321));
                        cached.put(id, new org.joml.Vector2i(Integer.MAX_VALUE, Integer.MIN_VALUE));
                        Files.writeString(control.resolve("command"), "world-nether"); phase = 5; ticks = 0;
                    }
                    case 5 -> {
                        if (ticks < 60 || renderedLevel != client.level || client.gui.screen() != null || !client.level.dimension().equals(net.minecraft.world.level.Level.NETHER)) return;
                        check(!((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).containsKey(staleWorldId), "rendered world transition discards map ID belonging only to old cache");
                        Files.writeString(control.resolve("command"), "world-overworld"); phase = 6; ticks = 0;
                    }
                    case 6 -> {
                        if (ticks < 60 || renderedLevel != client.level || client.gui.screen() != null || !client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
                        check(((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).get(id) != null, "returning to overworld rebuilds minimap map center");
                        var atlas = ModClientUtil.getFirstItem(client, ModItems.ATLAS);
                        var expectedCenter = atlas.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).items().getFirst().get(ModDataComponents.MAP_CENTER);
                        check(expectedCenter.equals(((Map<?, ?>) field(MinimapOverlay.class, "CACHED_CENTERS")).get(id)), "returning world replaces reused ID's incorrect cached center with atlas center");
                        check(client.level.getMapData(id) != null, "returning to overworld receives map render data");
                        evidence.add("dimension round trip clears and rebuilds minimap cache");
                        Files.writeString(control.resolve("command"), "craft-setup"); phase = 7; ticks = 0;
                    }
                    case 7 -> {
                        if (ticks < 60) return;
                        var result = client.player.inventoryMenu.getSlot(0).getItem();
                        check(result.is(ModItems.ATLAS), "native crafting menu receives authoritative atlas result");
                        check(result.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).items().getFirst().count() == 1, "native recipe result contains only one seed map");
                        client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, client.player);
                        phase = 8; ticks = 0;
                    }
                    case 8 -> {
                        if (ticks < 40) return;
                        var carried = client.player.inventoryMenu.getCarried();
                        check(carried.is(ModItems.ATLAS) && carried.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).items().getFirst().count() == 1, "native client actual crafting click receives one-map atlas");
                        check(client.player.inventoryMenu.getSlot(1).getItem().getCount() == 7 && client.player.inventoryMenu.getSlot(2).getItem().getCount() == 15, "native client observes one book and one map consumed");
                        Files.writeString(control.resolve("command"), "craft-check"); phase = 9; ticks = 0;
                    }
                    case 9 -> {
                        String response = Files.readString(control.resolve("ack"));
                        if (!response.startsWith("craft PASS")) return;
                        evidence.add(response);
                        finish();
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace(); done = true;
                try { Files.writeString(control.resolve("result.txt"), "FAIL phase=" + phase + " " + failure + "\n" + String.join("\n", evidence)); } catch (Exception ignored) {}
            }
        });
    }
    void finish() throws Exception {
        Files.writeString(control.resolve("result.txt"), "PASS " + checks + " client assertions; real client/server Tool Pouch smoke\n" + String.join("\n", evidence) + "\n");
        done = true;
    }
}
