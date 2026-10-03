package shulkerqa;

import java.nio.file.*;
import java.util.*;
import me.pajic.toolpouch.network.ModPayloads;
import me.pajic.toolpouch.keybind.ModKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.entity.item.ItemEntity;

public class ShulkerClientQa implements ClientModInitializer {
    final Path control = Path.of(System.getProperty("shulker.qa.control"));
    final String[] cases = {"ordinary", "reopen-before-close", "move-owner", "drop-owner", "swap-two-pouches", "deposit-before-reopen", "parent-live-reorder", "withdraw-then-move-owner", "withdraw-then-drop-owner", "leggings-beds", "leggings-beds-x-repeat"};
    int scenario = Integer.getInteger("shulker.qa.startCase", 0), phase, ticks, total, defects, clientDefects;
    final boolean singleCase = Boolean.getBoolean("shulker.qa.singleCase");
    boolean done;
    String pending, expected;
    void command(String command) throws Exception { pending = command; Files.writeString(control.resolve("command"), command); ticks = 0; }
    String ack() throws Exception {
        if (ticks < 15 || !Files.exists(control.resolve("ack"))) return null;
        var text = Files.readString(control.resolve("ack")).split("\n", 2);
        return text[0].equals(pending) ? text[1] : null;
    }
    void record(String text) throws Exception { Files.writeString(control.resolve("observations.txt"), cases[scenario] + ": " + text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
    void open() { ClientPlayNetworking.send(new ModPayloads.C2SOpenShulkerBoxPayload(0)); ticks = 0; }
    void click(Minecraft client, int slot, int button, ContainerInput type) { client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slot, button, type, client.player); ticks = 0; }
    void menu(Minecraft client) { if (!(client.player.containerMenu instanceof ShulkerBoxMenu)) throw new AssertionError("no native child ShulkerBoxMenu at " + cases[scenario] + "/" + phase); }
    int bedSlot(Minecraft client, String name) {
        for (int i = 27; i < 63; i++) {
            var stack = client.player.containerMenu.getSlot(i).getItem();
            var label = stack.get(DataComponents.CUSTOM_NAME);
            if (label != null && label.getString().equals(name)) return i;
        }
        throw new AssertionError("withdrawn bed missing: " + name);
    }
    int inventoryBedSlot(Minecraft client) {
        for (int i=0;i<client.player.inventoryMenu.slots.size();i++) {
            var label=client.player.inventoryMenu.getSlot(i).getItem().get(DataComponents.CUSTOM_NAME);
            if (label != null && label.getString().equals("QA-bed-1")) return i;
        }
        throw new AssertionError("bed1 missing from main inventory");
    }
    void traceBeds(Minecraft client, String action) throws Exception {
        record(action + " menu=" + client.player.containerMenu.containerId + " child0=" + client.player.containerMenu.getSlot(0).getItem() + " child1=" + client.player.containerMenu.getSlot(1).getItem() + " child2=" + client.player.containerMenu.getSlot(2).getItem() + " cursor=" + client.player.containerMenu.getCarried());
    }
    void count(ItemStack stack, Map<String,Integer> counts, int depth) {
        if (stack.isEmpty()) return;
        if (depth > 16) throw new AssertionError("unexpected client nested container depth");
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getString().startsWith("QA-")) counts.merge(name.getString() + "@damage=" + stack.getDamageValue(), stack.getCount(), Integer::sum);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().forEach(child -> count(child, counts, depth+1));
    }
    String clientSnapshot(Minecraft client) {
        Map<String,Integer> counts = new TreeMap<>();
        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) count(client.player.getInventory().getItem(i), counts, 0);
        count(client.player.inventoryMenu.getCarried(), counts, 0);
        for (var entity : client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(64))) count(entity.getItem(), counts, 0);
        return counts.toString();
    }
    void audit(Minecraft client, String snapshot, String stage) throws Exception {
        boolean match = expected.equals(snapshot);
        total++;
        if (!match) defects++;
        record(stage + " " + (match ? "CONSERVED" : "PERSISTENT_MISMATCH") + " expected=" + expected + " actual=" + snapshot);
        String local = clientSnapshot(client);
        if (!local.equals(snapshot)) clientDefects++;
        record(stage + " settled-client=" + local + " client_matches_server=" + local.equals(snapshot));
    }
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.player == null || client.level == null) return;
            try {
                if (++ticks > 900) throw new AssertionError("timeout " + cases[scenario] + "/" + phase);
                if (scenario == 10 && phase >= 303 && phase <= 309 && ticks % 6 == 0) ModKeybinds.OPEN_WIDGET.setDown(true);
                String reply;
                switch (phase) {
                    case 0 -> {
                        if (ticks < 60) return;
                        if (Boolean.getBoolean("shulker.qa.reconnect")) {
                            expected = Files.readString(control.resolve("expected.txt"));
                            scenario = cases.length-1;
                            command("inspect-after-process-restart"); phase = 900;
                        } else { command("seed " + cases[scenario]); phase = 1; }
                    }
                    case 900 -> { if ((reply = ack()) == null) return; audit(client,reply,"after process restart"); open();phase=901; }
                    case 901 -> { if (ticks < 20) return;menu(client);client.player.closeContainer();ticks=0;phase=902; }
                    case 902 -> { if (ticks < 20) return;command("inspect-reopened-after-process-restart");phase=903; }
                    case 903 -> {
                        if ((reply = ack()) == null) return;audit(client,reply,"after process restart and reopen");
                        Files.writeString(control.resolve("result.txt"),"COMPLETE " + total + " restart conservation checks; persistent mismatches=" + defects + "; client_mismatches=" + clientDefects + "\n");done=true;
                    }
                    case 1 -> {
                        if ((reply = ack()) == null) return; expected = reply; record("seed=" + expected);
                        if (scenario >= 9) { ModKeybinds.OPEN_WIDGET.setDown(true); ticks = 0; phase = 300; }
                        else if (scenario == 6) { ClientPlayNetworking.send(new ModPayloads.C2SOpenToolPouchPayload(0)); ticks = 0; phase = 200; }
                        else { open(); phase = 2; }
                    }
                    case 200 -> {
                        if (ticks < 20) return;
                        if (!(client.player.containerMenu instanceof me.pajic.toolpouch.menu.ToolPouchMenu)) throw new AssertionError("parent ToolPouchMenu missing");
                        click(client, 0, 0, ContainerInput.PICKUP); phase = 201;
                    }
                    case 201 -> { if (ticks < 20) return; click(client, 1, 0, ContainerInput.PICKUP); phase = 202; }
                    case 202 -> { if (ticks < 20) return; click(client, 0, 0, ContainerInput.PICKUP); phase = 203; }
                    case 203 -> { if (ticks < 20) return; record("live parent slot0=" + client.player.containerMenu.getSlot(0).getItem()); open(); phase = 2; }
                    case 300 -> { if (ticks < 20) return; ModKeybinds.OPEN_WIDGET.setDown(false); ticks = 0; phase = 301; }
                    case 301 -> { if (ticks < 20) return; menu(client); traceBeds(client,"actual X opened child"); click(client,0,0,ContainerInput.QUICK_MOVE); phase=303; }
                    case 303 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed1 withdrawn"); click(client,bedSlot(client,"QA-bed-1"),0,ContainerInput.PICKUP); phase=304; }
                    case 304 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed1 picked up from inventory"); click(client,27,0,ContainerInput.PICKUP); phase=305; }
                    case 305 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed1 placed inventory slot27"); click(client,27,0,ContainerInput.PICKUP); phase=306; }
                    case 306 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed1 moved again"); click(client,28,0,ContainerInput.PICKUP); phase=307; }
                    case 307 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed1 placed inventory slot28"); click(client,1,0,ContainerInput.QUICK_MOVE); phase=308; }
                    case 308 -> { if (ticks < 20) return; menu(client); traceBeds(client,"bed2 withdrawn"); click(client,2,0,ContainerInput.QUICK_MOVE); phase=309; }
                    case 309 -> { if (ticks < 20) return; menu(client); ModKeybinds.OPEN_WIDGET.setDown(false); traceBeds(client,"bed3 withdrawn"); client.player.closeContainer();ticks=0;phase=8; }
                    case 2 -> {
                        if (ticks < 20) return; menu(client);
                        if (scenario == 2) { click(client, 54, 8, ContainerInput.SWAP); phase = 3; }
                        else if (scenario == 3) { click(client, 54, 1, ContainerInput.THROW); phase = 3; }
                        else if (scenario == 4) { click(client, 54, 1, ContainerInput.SWAP); phase = 3; }
                        else phase = 3;
                    }
                    case 3 -> {
                        if (ticks < 20) return;
                        if (scenario >= 2 && scenario <= 4 && !(client.player.containerMenu instanceof ShulkerBoxMenu)) {
                            record("owner mutation safely closed child before further withdrawal"); ticks = 0; phase = 8; return;
                        }
                        menu(client);
                        if (scenario == 5) { click(client, 56, 0, ContainerInput.QUICK_MOVE); phase = 4; return; }
                        record("before withdrawal slot0=" + client.player.containerMenu.getSlot(0).getItem());
                        click(client, 0, 0, ContainerInput.QUICK_MOVE); phase = 4;
                    }
                    case 4 -> {
                        if (ticks < 20) return; menu(client);
                        if (scenario == 5) { record("deposited pickaxe slot3=" + client.player.containerMenu.getSlot(3).getItem()); open(); phase = 60; return; }
                        record("settled client slot0=" + client.player.containerMenu.getSlot(0).getItem());
                        click(client, 1, 0, ContainerInput.QUICK_MOVE); phase = 5;
                    }
                    case 5 -> {
                        if (ticks < 20) return; menu(client);
                        if (scenario == 1) { open(); phase = 6; }
                        else if (scenario == 7) { click(client, 54, 8, ContainerInput.SWAP); phase = 71; }
                        else if (scenario == 8) { click(client, 54, 1, ContainerInput.THROW); phase = 71; }
                        else { client.player.closeContainer(); ticks = 0; phase = 8; }
                    }
                    case 6 -> {
                        if (ticks < 20) return; menu(client);
                        record("reopened before close slot0=" + client.player.containerMenu.getSlot(0).getItem() + " slot1=" + client.player.containerMenu.getSlot(1).getItem());
                        click(client, 0, 0, ContainerInput.QUICK_MOVE); phase = 7;
                    }
                    case 7 -> { if (ticks < 20) return; menu(client); click(client, 1, 0, ContainerInput.QUICK_MOVE); phase = 70; }
                    case 60 -> { if (ticks < 20) return; menu(client); record("reopened deposit slot3=" + client.player.containerMenu.getSlot(3).getItem()); client.player.closeContainer(); ticks = 0; phase = 8; }
                    case 70 -> { if (ticks < 20) return; client.player.closeContainer(); ticks = 0; phase = 8; }
                    case 71 -> { if (ticks < 20) return; record("owner moved after withdrawals; child-open=" + (client.player.containerMenu instanceof ShulkerBoxMenu)); client.player.closeContainer(); ticks = 0; phase = 8; }
                    case 8 -> { if (ticks < 20) return; command("inspect-closed " + cases[scenario]); phase = 9; }
                    case 9 -> {
                        if ((reply = ack()) == null) return; audit(client, reply, "after close");
                        if(scenario==9){client.gui.setScreen(new InventoryScreen(client.player));ticks=0;phase=910;}
                        else {open(); phase = 10;}
                    }
                    case 910 -> {if(ticks<20)return;click(client,inventoryBedSlot(client),0,ContainerInput.PICKUP);phase=911;}
                    case 911 -> {if(ticks<20)return;click(client,11,0,ContainerInput.PICKUP);phase=912;}
                    case 912 -> {if(ticks<20)return;click(client,11,0,ContainerInput.PICKUP);phase=913;}
                    case 913 -> {if(ticks<20)return;click(client,12,0,ContainerInput.PICKUP);phase=914;}
                    case 914 -> {if(ticks<20)return;client.player.closeContainer();command("inspect-main-inventory-movement");phase=915;}
                    case 915 -> {if((reply=ack())==null)return;audit(client,reply,"after ordinary inventory screen movement");open();phase=10;}
                    case 10 -> {
                        if (ticks < 20) return; menu(client);
                        record("fresh reopen slot0=" + client.player.containerMenu.getSlot(0).getItem() + " slot1=" + client.player.containerMenu.getSlot(1).getItem());
                        client.player.closeContainer(); ticks = 0; phase = 11;
                    }
                    case 11 -> { if (ticks < 20) return; command("inspect-reopened " + cases[scenario]); phase = 12; }
                    case 12 -> {
                        if ((reply = ack()) == null) return; audit(client, reply, "after reopen and close");
                        if (singleCase || ++scenario == cases.length) {
                            Files.writeString(control.resolve("result.txt"), "COMPLETE " + total + " authoritative conservation checks; persistent mismatches=" + defects + "; client_mismatches=" + clientDefects + "\n"); done = true;
                        } else { phase = 0; ticks = 40; }
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace(); done = true;
                try { Files.writeString(control.resolve("result.txt"), "ERROR scenario=" + scenario + " phase=" + phase + " " + failure); } catch (Exception ignored) {}
            }
        });
    }
}
