package com.thenathe.mapstitchcompat.mixin;

import java.util.ArrayList;
import me.pajic.mapstitch.component.ModDataComponents;
import me.pajic.mapstitch.item.AtlasItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repair persisted atlases too: nested maps never receive vanilla's direct-item sync hook. */
@Mixin(value = AtlasItem.class, remap = false)
public abstract class AtlasMetadataMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void multiShim$repairMaps(ItemStack atlas, ServerLevel level, Entity owner, EquipmentSlot slot, CallbackInfo ci) {
        BundleContents contents = atlas.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        int activeId = atlas.getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1);
        boolean activePresent = false;
        ArrayList<ItemStack> repaired = null;
        for (int i = 0; i < contents.size(); i++) {
            var template = contents.items().get(i);
            var id = template.get(DataComponents.MAP_ID);
            if (id == null) continue;
            var data = MapItem.getSavedData(id, level);
            if (data == null) continue;
            if (id.id() == activeId && data.dimension.equals(level.dimension())) activePresent = true;
            Vector2i center = template.get(ModDataComponents.MAP_CENTER);
            if (center == null || center.x != data.centerX || center.y != data.centerZ) {
                if (repaired == null) repaired = new ArrayList<>(contents.itemCopies().toList());
                repaired.get(i).set(ModDataComponents.MAP_CENTER, new Vector2i(data.centerX, data.centerZ));
            }
        }
        if (repaired != null) {
            // copyWithContents/tryInsert apply ordinary bundle capacity and can discard atlas maps.
            var updated = new BundleContents(repaired.stream().map(ItemStackTemplate::fromNonEmptyStack).toList());
            if (contents.getSelectedItemIndex() != BundleContents.NO_SELECTED_ITEM_INDEX) {
                var mutable = updated.asMutable();
                mutable.toggleSelectedItem(contents.getSelectedItemIndex());
                updated = mutable.toImmutable();
            }
            atlas.set(DataComponents.BUNDLE_CONTENTS, updated);
        }
        // A stale ID otherwise prevents the upstream selection routine from running forever.
        if (activeId != -1 && !activePresent) atlas.set(ModDataComponents.ATLAS_ACTIVE_MAP_ID, -1);
    }
}
