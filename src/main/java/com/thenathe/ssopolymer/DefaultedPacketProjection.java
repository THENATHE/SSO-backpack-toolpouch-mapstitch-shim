package com.thenathe.ssopolymer;

import net.atlas.defaulted.component.ItemPatches;
import net.atlas.defaulted.networking.ClientboundDefaultComponentsSyncPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Keep scoped Defaulted patches scoped when Polymer hides their target items. */
public final class DefaultedPacketProjection {
    private DefaultedPacketProjection() {}

    public static ClientboundDefaultComponentsSyncPacket project(
            ClientboundDefaultComponentsSyncPacket original, Predicate<Holder<Item>> visible) {
        var projected = new ArrayList<ItemPatches>();
        boolean changed = false;
        for (var patch : original.list()) {
            var targets = new LinkedHashSet<Holder<Item>>();
            patch.elements().forEach(set -> set.forEach(targets::add));
            // Defaulted 1.3.8 deliberately treats an empty selector, including all-empty
            // server holder sets, as global. Do not reinterpret those wildcard patches.
            if (targets.isEmpty()) {
                projected.add(patch);
                continue;
            }
            var retained = targets.stream().filter(visible).toList();
            if (!retained.isEmpty()) {
                // Named tags may lose every member on the client. Explicit visible targets
                // cannot turn into Defaulted's all-empty => every-item selector.
                projected.add(new ItemPatches(List.of(HolderSet.direct(retained)), patch.generators(),
                        patch.dataComponentPatch(), patch.priority()));
            }
            // An originally scoped patch with no client targets must not become global.
            changed = true;
        }
        return changed ? new ClientboundDefaultComponentsSyncPacket(projected) : original;
    }
}
