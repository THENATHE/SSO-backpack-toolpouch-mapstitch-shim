package qa;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import java.nio.file.*;
import java.util.*;

/** Test-only optional-module checks; deliberately has no references to original-mod classes. */
public final class MatrixQa implements ModInitializer {
    private final Path control = Path.of(System.getProperty("matrix.qa.control"));
    private final Set<String> expected = new HashSet<>(Arrays.asList(System.getProperty("matrix.qa.mods", "").split(",")));
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> check(s, "startup", true));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s,r,ok) -> check(s, "reload", ok));
    }
    private void check(MinecraftServer server, String phase, boolean reloadOk) {
        try {
            if (!reloadOk) throw new AssertionError("Datapack reload failed");
            var combinedExpected = new HashSet<>(expected); combinedExpected.remove(""); combinedExpected.remove("chalk");
            var enabled = (Collection<?>) Class.forName("com.thenathe.combinedshim.Modules").getMethod("enabled").invoke(null);
            if (!new HashSet<>(enabled).equals(combinedExpected)) throw new AssertionError("Wrong unified modules: " + enabled);
            var counts = new LinkedHashMap<String,Integer>();
            for (String id : List.of("chalk", "mapstitch", "simple_smithing_overhaul", "tiered_backpacks", "toolpouch")) {
                boolean present = expected.contains(id);
                if (FabricLoader.getInstance().isModLoaded(id) != present) throw new AssertionError("Wrong loaded state: " + id);
                int count = 0;
                for (var item : BuiltInRegistries.ITEM) {
                    var key = BuiltInRegistries.ITEM.getKey(item);
                    if (!key.getNamespace().equals(id)) continue;
                    if (!present) throw new AssertionError("Absent module registered item: " + key);
                    var original = new ItemStack(item);
                    var client = PolymerItemUtils.getPolymerItemStack(original, null, server.registryAccess());
                    if (client.isEmpty() || !BuiltInRegistries.ITEM.getKey(client.getItem()).getNamespace().equals("minecraft"))
                        throw new AssertionError("Unsafe fallback for " + key + ": " + client);
                    if (original.getItem() != item || original.getCount() != 1) throw new AssertionError("Original stack mutated: " + key);
                    count++;
                }
                if (present && count == 0) throw new AssertionError("Present module has no registered items: " + id);
                counts.put(id, count);
            }
            if (expected.contains("simple_smithing_overhaul")) repair(server);
            Files.writeString(control.resolve(phase + ".txt"), "PASS " + phase + " original item identities retained; vanilla item fallbacks=" + counts
                    + (expected.contains("simple_smithing_overhaul") ? "; enchanted SSO repair damage1000->479, matching materials/enchantments/remainder verified" : ""));
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(control.resolve(phase + ".txt"), "FAIL " + failure); } catch (Exception ignored) {}
        }
    }
    private void repair(MinecraftServer server) throws Exception {
        // Resolve only when the optional mod is present; retain the exact developer/port recipe.
        var recipe = (CustomRecipe) Class.forName("me.pajic.simple_smithing_overhaul.recipe.PortableItemRepairRecipe").getConstructor().newInstance();
        var pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.setDamageValue(1000);
        var stone = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("simple_smithing_overhaul:whetstone")));
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var key : List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING)) {
            var enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            pick.enchant(enchantment,1); stored.set(enchantment,1);
        }
        stone.set(DataComponents.STORED_ENCHANTMENTS,stored.toImmutable());
        var input=CraftingInput.of(3,1,List.of(pick,new ItemStack(Items.DIAMOND),stone));
        if (!recipe.matches(input,server.overworld())) throw new AssertionError("SSO repair did not match");
        var output=recipe.assemble(input);
        if (!output.is(Items.DIAMOND_PICKAXE) || output.getDamageValue()!=479 || pick.getDamageValue()!=1000)
            throw new AssertionError("SSO repair damage/input mutation: "+output);
        if (!output.getEnchantments().equals(pick.getEnchantments())) throw new AssertionError("SSO repair enchantment loss");
        var remaining=recipe.getRemainingItems(input);
        if (!remaining.get(0).isEmpty() || !remaining.get(1).isEmpty() || !remaining.get(2).is(stone.getItem()))
            throw new AssertionError("SSO incorrect material/whetstone remainder");
        if (recipe.matches(CraftingInput.of(3,1,List.of(pick,new ItemStack(Items.IRON_INGOT),stone)),server.overworld()))
            throw new AssertionError("SSO accepted wrong repair material");
        if (recipe.matches(CraftingInput.of(3,1,List.of(pick,new ItemStack(Items.DIAMOND),new ItemStack(Items.FLINT))),server.overworld()))
            throw new AssertionError("SSO accepted flint for enchanted gear");
    }
}
