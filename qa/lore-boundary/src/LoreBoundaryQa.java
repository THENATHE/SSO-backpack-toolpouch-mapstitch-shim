package qa;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Isolates the legal maximum-lore failure before the other storage assertions run. */
public final class LoreBoundaryQa implements ModInitializer {
    private static List<Map.Entry<net.minecraft.network.chat.Style,String>> styledRuns(Component component) {
        var runs = new ArrayList<Map.Entry<net.minecraft.network.chat.Style,String>>();
        component.visit((style,text) -> { if (!text.isEmpty()) runs.add(Map.entry(style,text)); return Optional.<Void>empty(); }, net.minecraft.network.chat.Style.EMPTY);
        return runs;
    }
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String result;
            try {
                boolean expectOverflow = Boolean.getBoolean("lore.qa.expectOverflow");
                var context = ((PacketContextProvider)(Object)new Connection(PacketFlow.SERVERBOUND)).getPacketContext();
                int cases = 0;
                for (String id : List.of("toolpouch:tool_pouch", "toolpouch:netherite_tool_pouch")) {
                    var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                    if (item == null || item == Items.AIR) throw new AssertionError("Missing " + id);
                    for (int size : new int[]{ItemLore.MAX_LINES - 1, ItemLore.MAX_LINES}) {
                        var stack = new ItemStack(item);
                        var lines = new ArrayList<Component>();
                        for (int i = 0; i < size; i++) lines.add(Component.literal("Original lore " + i));
                        stack.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
                        var saved = stack.copy();
                        boolean overflow = false;
                        try {
                            var wire = PolymerItemUtils.getPolymerItemStack(stack, TooltipFlag.NORMAL, context, server.registryAccess());
                            var shown = wire.get(DataComponents.LORE).lines();
                            if (shown.size() != ItemLore.MAX_LINES || !shown.getLast().getString().contains("Tool Pouch mod")) {
                                throw new AssertionError("Invalid projected lore/notice " + id + " " + size);
                            }
                            if (size == ItemLore.MAX_LINES && !shown.getLast().getString().startsWith("Original lore 255\n")) {
                                throw new AssertionError("Last original lore entry discarded");
                            }
                        } catch (IllegalArgumentException error) {
                            if (!error.getMessage().contains("256")) throw error;
                            overflow = true;
                            System.out.println("LORE_BOUNDARY_REPRODUCED " + id + " " + error);
                        }
                        if (overflow != (expectOverflow && size == ItemLore.MAX_LINES)) {
                            throw new AssertionError("Unexpected overflow=" + overflow + " " + id + " " + size);
                        }
                        if (!ItemStack.matches(stack, saved)) throw new AssertionError("Source mutated " + id);
                        cases++;
                    }
                }
                if (!expectOverflow) {
                  for (String id : List.of("tiered_backpacks:leather_backpack", "toolpouch:tool_pouch", "toolpouch:netherite_tool_pouch")) {
                   for (int size : new int[]{ItemLore.MAX_LINES - 1, ItemLore.MAX_LINES}) {
                    var full = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
                    var lines = new ArrayList<Component>();
                    for (int i = 0; i < size; i++) lines.add(Component.literal("Full item lore " + i).withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD));
                    full.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
                    full.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 7))));
                    full.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x123456));
                    var saved = full.copy();
                    var wire = PolymerItemUtils.getPolymerItemStack(full, TooltipFlag.NORMAL, context, server.registryAccess());
                    var shown = wire.get(DataComponents.LORE).lines();
                    var text = String.join("\n", shown.stream().map(Component::getString).toList());
                    if (shown.size() != ItemLore.MAX_LINES) throw new AssertionError("Unexpected compound tooltip entry count " + shown.size());
                    var actualRuns = wire.get(DataComponents.LORE).styledLines().stream().flatMap(line -> styledRuns(line).stream()).toList();
                    for (var line : full.get(DataComponents.LORE).styledLines()) {
                        if (!actualRuns.containsAll(styledRuns(line))) throw new AssertionError("Compound tooltip lost text/style " + id + " " + size + " " + line);
                    }
                    if (!text.contains(id.startsWith("tiered_backpacks:") ? "Tiered Backpacks mod" : "Tool Pouch mod")) throw new AssertionError("Compound tooltip lost notice");
                    if (!ItemStack.matches(full, saved)) throw new AssertionError("Compound tooltip mutated source");
                    if (!ItemStack.matches(PolymerItemUtils.getRealItemStack(wire, context, server.registryAccess()), saved)) throw new AssertionError("Compound tooltip damaged roundtrip data");
                    cases++;
                   }
                  }
                }
                result = "PASS " + cases + " cases; " + (expectOverflow ? "baseline maximum-lore exception reproduced for both pouches" : "255/256-entry plain pouch lore and compound backpack/both-pouch tooltips safely projected; every custom text/style and serialized roundtrip preserved") + "; source unchanged";
            } catch (Throwable error) {
                error.printStackTrace();
                result = "FAIL " + error;
            }
            try { Files.writeString(Path.of(System.getProperty("lore.qa.result")), result + "\n"); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
}
