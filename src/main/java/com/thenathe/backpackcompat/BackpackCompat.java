package com.thenathe.backpackcompat;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import me.pajic.tiered_backpacks.component.ModDataComponents;
import me.pajic.tiered_backpacks.item.ModItems;
import me.pajic.tiered_backpacks.menu.ModMenuTypes;
import me.pajic.tiered_backpacks.recipe.ModRecipes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime overlays only: real registry entries and stored contents stay untouched. */
public final class BackpackCompat {
    public static final Component NOTICE = Component.literal("You need the Tiered Backpacks mod to use this backpack.");
    public static final PacketContext.Key<Boolean> NATIVE = PacketContext.key(Identifier.parse("tiered_backpacks_polymer_compat:native"));

    public static boolean nativeClient(PacketContext context) {
        return com.thenathe.combinedshim.NativeClients.isNative(context, "tiered_backpacks");
    }

    public static boolean ownEntry(Identifier id) {
        return id != null && id.getNamespace().equals("tiered_backpacks");
    }

    /** Native registry coordination also covers SSO entries when that optional mod is present. */
    public static boolean negotiatedEntry(Identifier id) {
        return ownEntry(id) || (id != null && id.getNamespace().equals("simple_smithing_overhaul"));
    }

    private static final Map<UUID, Integer> NEXT_NOTICE = new HashMap<>();

    private BackpackCompat() {}

    public static void initialize() {
        register(ModItems.LEATHER_BACKPACK, "Leather Backpack");
        register(ModItems.COPPER_BACKPACK, "Copper Backpack");
        register(ModItems.IRON_BACKPACK, "Iron Backpack");
        register(ModItems.GOLDEN_BACKPACK, "Golden Backpack");
        register(ModItems.DIAMOND_BACKPACK, "Diamond Backpack");
        register(ModItems.NETHERITE_BACKPACK, "Netherite Backpack");
        // These also occur on vanilla chestplates with attached backpacks.
        PolymerComponent.registerDataComponent(ModDataComponents.BACKPACK_TIER, ModDataComponents.STORED_BACKPACK_DYE);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.MENU, ModMenuTypes.BACKPACK_MENU);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, ModRecipes.ATTACH_BACKPACK);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, ModRecipes.DETACH_BACKPACK);

        // Upstream item definitions use only vanilla model and dye codecs.
        PolymerResourcePackUtils.addModAssets("tiered_backpacks");
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            try (var license = BackpackCompat.class.getResourceAsStream("/licenses/tiered-backpacks-LICENSE.txt")) {
                if (license == null) throw new IllegalStateException("Missing Tiered Backpacks license");
                builder.addData("licenses/tiered_backpacks/LICENSE", license.readAllBytes());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT_NOTICE.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_NOTICE.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTickCount();
            for (var player : server.getPlayerList().getPlayers()) {
                if (nativeClient(player.connection.getPacketContext()) || !needsNotice(player)) {
                    NEXT_NOTICE.remove(player.getUUID());
                } else if (now - NEXT_NOTICE.getOrDefault(player.getUUID(), now) >= 0) {
                    notice(player);
                    NEXT_NOTICE.put(player.getUUID(), now + 200);
                }
            }
        });
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack.getItem() instanceof me.pajic.tiered_backpacks.item.BackpackItem;
    }

    public static boolean needsNotice(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) {
            var stack = player.getItemBySlot(slot);
            if (isBackpack(stack) || me.pajic.tiered_backpacks.util.BackpackUtil.isChestplateWithBackpackAttached(stack)) return true;
        }
        var accessories = me.pajic.tiered_backpacks.compat.AccessoryUtil.INSTANCE;
        return accessories != null && isBackpack(accessories.getBackpack(player));
    }

    public static void notice(ServerPlayer player) {
        player.sendOverlayMessage(NOTICE);
    }

    private static void register(Item item, String name) {
        PolymerItem.registerOverlay(item, new BackpackOverlay(name));
    }

    private record BackpackOverlay(String name) implements PolymerItem {
        @Override
        public boolean canSyncRawToClient(PacketContext context) { return nativeClient(context); }
        @Override
        public Item getPolymerItem(ItemStack stack, PacketContext context) {
            return nativeClient(context) ? stack.getItem() : Items.LEATHER;
        }

        @Override
        public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
            if (nativeClient(context)) return stack.get(DataComponents.ITEM_MODEL);
            return context != null && PolymerResourcePackUtils.hasMainPack(context)
                    ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : Identifier.withDefaultNamespace("leather");
        }

        @Override
        public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltip,
                                            PacketContext context, HolderLookup.Provider lookup) {
            if (nativeClient(context)) return stack;
            var result = PolymerItem.super.getPolymerItemStack(stack, tooltip, context, lookup);
            result.set(DataComponents.ITEM_NAME, Component.literal(name));
            result.set(DataComponents.LORE, new ItemLore(List.of(NOTICE)));
            // Change only the network copy. Preserve real contents, dye, name and tier on disk.
            result.remove(DataComponents.CONTAINER);
            result.remove(DataComponents.EQUIPPABLE);
            return result;
        }
    }
}
