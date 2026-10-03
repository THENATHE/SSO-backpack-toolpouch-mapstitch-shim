package com.thenathe.toolpouchcompat;

import eu.pb4.polymer.core.api.item.PolymerItem;
import com.thenathe.combinedshim.FallbackLore;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import me.pajic.toolpouch.component.ModDataComponents;
import me.pajic.toolpouch.compat.AccessoryUtil;
import me.pajic.toolpouch.item.ModItems;
import me.pajic.toolpouch.menu.ModMenuTypes;
import me.pajic.toolpouch.recipe.ModRecipes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** All replacements are wire-only. Authoritative stacks and stored contents remain intact. */
public final class ToolpouchCompat {
    public static final PacketContext.Key<Boolean> NATIVE = PacketContext.key(
            Identifier.parse("toolpouch_polymer_compat:native"));
    public static final Component NOTICE = Component.literal("You need the Tool Pouch mod on your client to use this.");
    private static final Map<UUID, Integer> NEXT_NOTICE = new HashMap<>();
    private static boolean initialized;

    private ToolpouchCompat() {}
    public static boolean nativeClient(PacketContext context) {
        return com.thenathe.combinedshim.NativeClients.isNative(context, "toolpouch");
    }
    public static boolean ownEntry(Identifier id) {
        return id != null && id.getNamespace().equals("toolpouch");
    }
    public static boolean isPouch(ItemStack stack) {
        return stack.is(ModItems.TOOL_POUCH) || stack.is(ModItems.NETHERITE_TOOL_POUCH);
    }
    public static boolean hasVisiblePouch(ServerPlayer player) {
        if (isPouch(player.getMainHandItem()) || isPouch(player.getOffhandItem())) return true;
        for (var slot : EquipmentSlot.values()) {
            var stack = player.getItemBySlot(slot);
            if (isPouch(stack) || me.pajic.toolpouch.util.GameplayUtil.isLeggingsWithPouchAttached(stack)) return true;
        }
        return AccessoryUtil.INSTANCE != null && !AccessoryUtil.INSTANCE.tryGetToolPouch(player).isEmpty();
    }
    public static void notice(ServerPlayer player) { player.sendOverlayMessage(NOTICE); }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PouchResources.initialize();
        PolymerItem.registerOverlay(ModItems.TOOL_POUCH, new PouchOverlay(false));
        PolymerItem.registerOverlay(ModItems.NETHERITE_TOOL_POUCH, new PouchOverlay(true));
        PolymerComponent.registerDataComponent(ModDataComponents.STORED_TOOL_POUCH_DYE, ModDataComponents.IS_NETHERITE_POUCH);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.MENU, ModMenuTypes.TOOL_POUCH_MENU);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, ModRecipes.ATTACH_TOOL_POUCH);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.RECIPE_SERIALIZER, ModRecipes.DETACH_TOOL_POUCH);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT_NOTICE.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTickCount();
            for (var player : server.getPlayerList().getPlayers()) {
                var id = player.getUUID();
                if (nativeClient(player.connection.getPacketContext())
                        || !hasVisiblePouch(player)) {
                    NEXT_NOTICE.remove(id);
                } else if (!NEXT_NOTICE.containsKey(id) || now - NEXT_NOTICE.get(id) >= 0) {
                    notice(player);
                    NEXT_NOTICE.put(id, now + 200);
                }
            }
        });
    }

    private record PouchOverlay(boolean netherite) implements PolymerItem {
        @Override public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, PacketContext context) {
            FallbackLore.fitTooltip(tooltip);
        }

        @Override public boolean canSyncRawToClient(PacketContext context) { return nativeClient(context); }
        @Override public Item getPolymerItem(ItemStack stack, PacketContext context) {
            return nativeClient(context) ? stack.getItem() : Items.LEATHER;
        }
        @Override public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
            return PouchResources.model(stack, context);
        }
        @Override public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag tooltip,
                PacketContext context, HolderLookup.Provider lookup) {
            if (nativeClient(context)) return stack;
            var result = PolymerItem.super.getPolymerItemStack(stack, tooltip, context, lookup);
            result.set(DataComponents.ITEM_NAME, Component.literal(netherite ? "Netherite Tool Pouch" : "Tool Pouch"));
            result.set(DataComponents.LORE, FallbackLore.withNotice(
                    result.getOrDefault(DataComponents.LORE, ItemLore.EMPTY), NOTICE));
            return result;
        }
    }
}
