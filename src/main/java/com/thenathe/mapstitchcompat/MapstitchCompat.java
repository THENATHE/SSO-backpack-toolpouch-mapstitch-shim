package com.thenathe.mapstitchcompat;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import me.pajic.mapstitch.component.ModDataComponents;
import me.pajic.mapstitch.item.ModItems;
import me.pajic.mapstitch.recipe.ModRecipes;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** Server-side overlays leave MapStitch's registered objects and saved data intact. */
public final class MapstitchCompat {
    public static final PacketContext.Key<Boolean> NATIVE = PacketContext.key(
            Identifier.parse("mapstitch_polymer_compat:native"));

    private MapstitchCompat() {}

    public static boolean nativeClient(PacketContext context) {
        // This is pinned when preparing registry sync, never upgraded by a late PLAY advertisement.
        return com.thenathe.combinedshim.NativeClients.isNative(context, "mapstitch");
    }

    public static boolean ownEntry(Identifier id) {
        return id != null && id.getNamespace().equals("mapstitch");
    }

    public static void initialize() {
        AtlasResources.initialize();
        PolymerItem.registerOverlay(ModItems.ATLAS, new AtlasOverlay());
        PolymerComponent.registerDataComponent(ModDataComponents.ATLAS_SCALE,
                ModDataComponents.ATLAS_FULLNESS, ModDataComponents.ATLAS_ACTIVE_MAP_ID,
                ModDataComponents.ATLAS_EJECT_FILLED_MAPS_FIRST, ModDataComponents.MAP_CENTER);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, ModRecipes.ATLAS);
        HeldAtlasNotice.initialize();
    }

    private static final class AtlasOverlay implements PolymerItem {
        @Override
        public boolean canSyncRawToClient(PacketContext context) {
            return nativeClient(context);
        }

        @Override
        public Item getPolymerItem(ItemStack stack, PacketContext context) {
            return nativeClient(context) ? ModItems.ATLAS : Items.BOOK;
        }

        @Override
        public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
            return AtlasResources.model(stack, context);
        }

        @Override
        public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltip,
                                             PacketContext context, HolderLookup.Provider lookup) {
            if (nativeClient(context)) return stack;
            var result = PolymerItem.super.getPolymerItemStack(stack, tooltip, context, lookup);
            result.set(DataComponents.ITEM_NAME, Component.literal("Atlas (MapStitch)"));
            return result;
        }
    }
}
