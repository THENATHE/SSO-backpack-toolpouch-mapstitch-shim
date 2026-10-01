package com.thenathe.ssopolymer;

import java.util.Map;
import java.util.WeakHashMap;
import com.thenathe.combinedshim.NativeClients;
import me.pajic.simple_smithing_overhaul.SSO;
import me.pajic.simple_smithing_overhaul.items.ModItems;
import me.pajic.simple_smithing_overhaul.util.CostAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-visible guidance replaces the original mod's client-only screen labels. */
public final class MenuGuidance {
    private record LastMessage(AbstractContainerMenu menu, String text, int tick) {}
    private static final Map<ServerPlayer, LastMessage> LAST = new WeakHashMap<>();
    private MenuGuidance() {}

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
        });
    }

    public static void tick(ServerPlayer player) {
        if (NativeClients.isNative(player.connection.getPacketContext(), "simple_smithing_overhaul")) {
            LAST.remove(player);
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        String text = describe(player, menu);
        LastMessage previous = LAST.get(player);
        if (text == null) {
            if (previous != null) {
                LAST.remove(player);
                player.sendOverlayMessage(Component.empty());
            }
            return;
        }
        if (previous == null || previous.menu() != menu || !previous.text().equals(text)
                || player.tickCount - previous.tick() >= 100) {
            player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
            LAST.put(player, new LastMessage(menu, text, player.tickCount));
        }
    }

    public static String describe(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu instanceof SmithingMenu smithing) {
            ItemStack template = smithing.getSlot(0).getItem();
            boolean pinnacle = template.is(ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE);
            boolean upgrade = template.is(ModItems.ENCHANTMENT_UPGRADE_SMITHING_TEMPLATE);
            if (!pinnacle && !upgrade) return null;
            if (smithing.getSlot(3).hasItem()) {
                int cost = (pinnacle || SSO.CONFIG.enchantmentUpgrading.upgradingHasExperienceCost.get())
                        ? ((CostAccess) smithing).sso$getCost() : 0;
                String selection = pinnacle ? "Random pinnacle upgrade" : "Enchantment #" + smithing.getSlot(2).getItem().getCount();
                return selection + ": " + costMessage(player, cost);
            }
            return pinnacle ? "Pinnacle: fully enchant your item, then add one Echo Shard."
                    : "Upgrade: add an enchanted item; Lapis count selects enchantment #1, #2, ...";
        }
        if (menu instanceof AnvilMenu anvil && anvil.getSlot(2).hasItem()) {
            return "Anvil: " + costMessage(player, anvil.getCost());
        }
        if (menu instanceof GrindstoneMenu grindstone
                && grindstone.getSlot(1).getItem().is(Items.NETHERITE_SCRAP)
                && SSO.CONFIG.grindstoneImprovements.repairCostReductionRecipe.get()) {
            return grindstone.getSlot(2).hasItem() ? "Halve prior work cost; keeps enchantments; no XP reward."
                    : "Use exactly one Netherite Scrap with an item that has prior work cost.";
        }
        return null;
    }

    private static String costMessage(ServerPlayer player, int cost) {
        if (cost == 0) return "Free. Take the result.";
        return cost + " XP levels" + (player.hasInfiniteMaterials() || player.experienceLevel >= cost
                ? ". Take the result." : " required; you have " + player.experienceLevel + ".");
    }
}
