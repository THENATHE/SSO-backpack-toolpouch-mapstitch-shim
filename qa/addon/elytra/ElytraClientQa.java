package elytraqa;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import java.util.*;
import com.thenathe.toolpouchcompat.CompatClient;
import com.thenathe.toolpouchcompat.ElytraPreference;
import me.pajic.toolpouch.util.ToolPouchUtil;
import me.pajic.toolpouch.keybind.ModKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Exercises the registered key mapping and unmodified production networking. */
public class ElytraClientQa implements ClientModInitializer {
    final Path control = Path.of(System.getProperty("elytra.qa.control"));
    final boolean reconnect = Boolean.getBoolean("elytra.qa.reconnect");
    final InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8);
    int phase, ticks, checks;
    Object oldPlayer;
    boolean done;
    String pending;

    void check(boolean value, String message) throws Exception {
        if (!value) throw new AssertionError(message);
        checks++;
        Files.writeString(control.resolve("assertions.txt"), (reconnect ? "reconnect " : "initial ") + message + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    boolean enabled(Minecraft client) { return ((ElytraPreference) client.player).toolpouchCompat$elytraEnabled(); }
    void command(String value) throws Exception { pending = value; Files.writeString(control.resolve("command"), value); ticks = 0; }
    String[] ack() throws Exception {
        if (ticks < 20 || !Files.exists(control.resolve("ack"))) return null;
        var result = Files.readString(control.resolve("ack")).split(" ");
        return result[0].equals(pending) ? result : null;
    }
    void state(Minecraft client, boolean expected, boolean glide) throws Exception {
        check(enabled(client) == expected, "client synchronized enabled=" + expected + " phase=" + phase);
        var method = LivingEntity.class.getDeclaredMethod("canGlide");
        method.setAccessible(true);
        boolean grounded = client.player.onGround();
        client.player.setOnGround(false);
        try { check((boolean) method.invoke(client.player) == glide, "client canGlide=" + glide + " phase=" + phase); }
        finally { client.player.setOnGround(grounded); }
        check((ToolPouchUtil.getElytraFromToolPouch(client.player, false) != null) == expected, "client functional pouch lookup phase=" + phase);
        check(ToolPouchUtil.getElytraFromToolPouch(client.player, true) != null, "cosmetic wings retained phase=" + phase);
    }
    void serverState(String[] ack, boolean expected, boolean glide, boolean cosmetic) throws Exception {
        check(Boolean.parseBoolean(ack[1]) == expected, "server enabled=" + expected + " command=" + pending);
        check(Boolean.parseBoolean(ack[2]) == glide, "server canGlide=" + glide + " command=" + pending);
        check(Boolean.parseBoolean(ack[3]) == cosmetic, "server cosmetic=" + cosmetic + " command=" + pending);
    }
    void click() { KeyMapping.click(key); ticks = 0; }
    void actionbar(Minecraft client, String expected) throws Exception {
        var field = client.gui.hud.getClass().getDeclaredField("overlayMessageString");
        field.setAccessible(true);
        Component message = (Component) field.get(client.gui.hud);
        check(message != null && message.getString().equals(expected), "actionbar: " + expected);
    }
    void controls(Minecraft client) throws Exception {
        var screen = new KeyBindsScreen(null, client.options);
        var list = new KeyBindsList(screen, client);
        String heading = ModKeybinds.MOD_KEYS.label().getString();
        long headings = list.children().stream()
            .filter(entry -> entry instanceof KeyBindsList.CategoryEntry)
            .filter(entry -> entry.children().stream().anyMatch(widget ->
                widget instanceof AbstractWidget text && text.getMessage().getString().equals(heading)))
            .count();
        check(headings == 1, "controls contain one Tool Pouch heading (actual=" + headings + ")");
        check(CompatClient.TOGGLE_ELYTRA.getCategory() == ModKeybinds.MOD_KEYS,
            "toggle shares upstream Tool Pouch category instance");
        check(Arrays.stream(client.options.keyMappings).filter(mapping ->
            mapping.getName().equals("key.toolpouch.toggle_elytra")).count() == 1,
            "toggle is registered exactly once");
    }
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done || client.player == null || client.level == null) return;
            try {
                if (++ticks > 600) throw new AssertionError("timeout phase=" + phase);
                String[] ack;
                switch (phase) {
                    case 0 -> {
                        if (ticks < 60) return;
                        check(CompatClient.TOGGLE_ELYTRA.getDefaultKey().equals(InputConstants.UNKNOWN), "toggle defaults to unbound");
                        check(Arrays.asList(client.options.keyMappings).contains(CompatClient.TOGGLE_ELYTRA), "toggle registered in controls");
                        controls(client);
                        CompatClient.TOGGLE_ELYTRA.setKey(key);
                        KeyMapping.resetMapping();
                        if (reconnect) { state(client, false, false); command("inspect-reconnect"); phase = 20; }
                        else { check(enabled(client), "new player defaults enabled"); command("seed-inventory"); phase = 1; }
                    }
                    case 1 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, true, true, true); state(client, true, true);
                        click(); phase = 2;
                    }
                    case 2 -> {
                        if (ticks < 10 || enabled(client)) return;
                        state(client, false, false); actionbar(client, "Tool Pouch Elytra disabled");
                        command("inspect-disabled"); phase = 3;
                    }
                    case 3 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, false, false, true); command("chest"); phase = 4;
                    }
                    case 4 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, false, true, true); state(client, false, true);
                        click(); phase = 5;
                    }
                    case 5 -> {
                        if (ticks < 10 || !enabled(client)) return;
                        state(client, true, true); actionbar(client, "Tool Pouch Elytra enabled");
                        command("seed-leggings"); phase = 6;
                    }
                    case 6 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, true, true, true); state(client, true, true);
                        click(); phase = 7;
                    }
                    case 7 -> {
                        if (ticks < 10 || enabled(client)) return;
                        state(client, false, false); oldPlayer = client.player;
                        command("respawn"); phase = 8;
                    }
                    case 8 -> {
                        if ((ack = ack()) == null || ticks < 60) return;
                        check(!enabled(client), "disabled preference synchronized after respawn");
                        check(!Boolean.parseBoolean(ack[1]), "server preserves disabled preference after respawn");
                        check(client.player != oldPlayer, "respawn replaced player entity");
                        command("seed-leggings-before-reconnect"); phase = 9;
                    }
                    case 9 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, false, false, true); state(client, false, false);
                        CompatClient.TOGGLE_ELYTRA.setKey(InputConstants.UNKNOWN);
                        KeyMapping.resetMapping();
                        Files.writeString(control.resolve("result.txt"), "RECONNECT " + checks + " initial assertions\n"); done = true;
                    }
                    case 20 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, false, false, true); click(); phase = 21;
                    }
                    case 21 -> {
                        if (ticks < 10 || !enabled(client)) return;
                        state(client, true, true); actionbar(client, "Tool Pouch Elytra enabled");
                        command("inspect-final"); phase = 22;
                    }
                    case 22 -> {
                        if ((ack = ack()) == null) return;
                        serverState(ack, true, true, true);
                        client.getConnection().sendCommand("toolpouch-elytra off");
                        ticks = 0; phase = 23;
                    }
                    case 23 -> {
                        if (ticks < 10 || enabled(client)) return;
                        state(client, false, false); actionbar(client, "Tool Pouch Elytra disabled");
                        // Explicit off must be idempotent and available to non-operator players.
                        client.getConnection().sendCommand("toolpouch-elytra off");
                        ticks = 0; phase = 24;
                    }
                    case 24 -> {
                        if (ticks < 30) return;
                        state(client, false, false);
                        command("change-dimension"); phase = 25;
                    }
                    case 25 -> {
                        if ((ack = ack()) == null || ticks < 60 || client.level.dimension() != Level.NETHER) return;
                        serverState(ack, false, false, true); state(client, false, false);
                        check(client.level.dimension() == Level.NETHER, "disabled preference resynchronized across dimension change");
                        client.getConnection().sendCommand("toolpouch-elytra on");
                        ticks = 0; phase = 26;
                    }
                    case 26 -> {
                        if (ticks < 10 || !enabled(client)) return;
                        state(client, true, true); actionbar(client, "Tool Pouch Elytra enabled");
                        client.getConnection().sendCommand("toolpouch-elytra");
                        ticks = 0; phase = 27;
                    }
                    case 27 -> {
                        if (ticks < 10 || enabled(client)) return;
                        state(client, false, false); actionbar(client, "Tool Pouch Elytra disabled");
                        CompatClient.TOGGLE_ELYTRA.setKey(InputConstants.UNKNOWN);
                        KeyMapping.resetMapping();
                        long total = Files.readAllLines(control.resolve("assertions.txt")).size();
                        Files.writeString(control.resolve("result.txt"), "PASS " + total + " assertions; Fabric 26.3 controls heading, registered key -> C2S toggle -> S2C shim state; inventory, leggings, chest fallback, respawn, reconnect, non-operator commands and dimension change\n"); done = true;
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace(); done = true;
                try { Files.writeString(control.resolve("result.txt"), "FAIL phase=" + phase + " " + failure + "\n"); } catch (Exception ignored) {}
            }
        });
    }
}
