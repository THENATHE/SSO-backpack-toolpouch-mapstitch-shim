package qa;

import com.thenathe.ssopolymer.DefaultedPacketProjection;
import net.atlas.defaulted.component.ItemPatches;
import net.atlas.defaulted.component.PatchGenerator;
import net.atlas.defaulted.networking.ClientboundDefaultComponentsSyncPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Test-only assertions over real Defaulted packets and the loaded Chalk item tag. */
public final class ProjectionQa implements ModInitializer {
    int checks;
    void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            String result;
            try {
                var chalk = BuiltInRegistries.ITEM.getOrThrow(TagKey.create(Registries.ITEM, Identifier.parse("chalk:chalks")));
                check(chalk.size() > 0, "actual server Chalk tag must contain items");
                int chalkSize = chalk.size();
                var diamond = Items.DIAMOND.builtInRegistryHolder();
                var data = DataComponentPatch.builder().set(DataComponents.REPAIRABLE,
                        new Repairable(HolderSet.direct(Items.CALCITE.builtInRegistryHolder()))).build();
                var generator = (PatchGenerator) Proxy.newProxyInstance(PatchGenerator.class.getClassLoader(),
                        new Class<?>[]{PatchGenerator.class}, (proxy, method, args) -> {
                            throw new AssertionError("projection must not execute generators");
                        });
                var generators = List.of(generator);
                var global = new ItemPatches(List.of(), generators, data, 731);
                var emptyGlobal = new ItemPatches(List.of(HolderSet.<Item>empty()), generators, data, 732);
                var visible = new ItemPatches(List.of(HolderSet.direct(diamond)), generators, data, 733);
                var mixed = new ItemPatches(List.of(HolderSet.<Item>empty(), chalk, HolderSet.direct(diamond)), generators, data, 734);
                var hidden = new ItemPatches(List.of(HolderSet.<Item>empty(), chalk), generators, data, 735);
                var originalList = new ArrayList<>(List.of(global, emptyGlobal, visible, mixed, hidden));
                var original = new ClientboundDefaultComponentsSyncPacket(originalList);
                var resultPacket = DefaultedPacketProjection.project(original, holder -> holder.value() == Items.DIAMOND);
                check(resultPacket != original, "scoped packet copy expected");
                check(resultPacket.list().size() == 4, "omit only hidden-only scoped patch");
                check(resultPacket.list().get(0) == global, "global wildcard patch unchanged");
                check(resultPacket.list().get(1) == emptyGlobal, "all-empty original wildcard unchanged");
                check(global.matchItem(Items.DIAMOND_PICKAXE), "original global wildcard applies");
                check(emptyGlobal.matchItem(Items.DIAMOND_PICKAXE), "original all-empty wildcard applies");
                for (int i : List.of(2, 3)) {
                    var patch = resultPacket.list().get(i);
                    check(patch.matchItem(Items.DIAMOND), "visible target retained");
                    check(!patch.matchItem(Items.DIAMOND_PICKAXE), "unrelated pick excluded");
                    check(!patch.matchItem(chalk.get(0).value()), "hidden Chalk excluded");
                    check(patch.elements().size() == 1 && patch.elements().getFirst().size() == 1,
                            "explicit nonempty visible target set");
                    check(patch.generators() == generators, "generator list preserved");
                    check(patch.dataComponentPatch() == data, "component patch preserved");
                    check(patch.priority() == 731 + i, "priority preserved");
                }
                check(original.list() == originalList && originalList.size() == 5, "original list untouched");
                check(original.list().get(3) == mixed && original.list().get(4) == hidden, "original entries untouched");
                check(chalk.size() == chalkSize && mixed.elements().size() == 3, "server tag and selectors untouched");
                check(hidden.matchItem(chalk.get(0).value()) && !hidden.matchItem(Items.DIAMOND_PICKAXE),
                        "original scoped patch still applies only to Chalk");
                var wildcardPacket = new ClientboundDefaultComponentsSyncPacket(new ArrayList<>(List.of(global, emptyGlobal)));
                check(DefaultedPacketProjection.project(wildcardPacket, holder -> false) == wildcardPacket,
                        "all-wildcard packet identity retained despite zero visibility");
                var hiddenPacket = new ClientboundDefaultComponentsSyncPacket(new ArrayList<>(List.of(hidden)));
                check(DefaultedPacketProjection.project(hiddenPacket, holder -> false).list().isEmpty(),
                        "hidden-only payload becomes empty list, never wildcard patch");
                var allVisible = DefaultedPacketProjection.project(hiddenPacket, holder -> true).list().getFirst();
                check(allVisible.elements().getFirst().size() == chalkSize, "all legitimate Chalk targets retained when visible");
                check(allVisible.matchItem(chalk.get(0).value()) && !allVisible.matchItem(Items.DIAMOND_PICKAXE),
                        "fully visible scoped patch remains scoped");
                result = "PASS " + checks + " assertions; actual Chalk tag=" + chalkSize
                        + "; global/all-empty wildcards, visible/mixed/hidden scoped selectors, generators/data/priority and original packet preserved";
            } catch (Throwable error) {
                error.printStackTrace(); result = "FAIL " + error;
            }
            try { Files.writeString(Path.of(System.getProperty("projection.qa.result")), result + "\n"); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
}
