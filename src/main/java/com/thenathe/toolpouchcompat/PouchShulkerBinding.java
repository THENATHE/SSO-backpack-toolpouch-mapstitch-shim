package com.thenathe.toolpouchcompat;

import com.thenathe.toolpouchcompat.mixin.ToolpouchAccess;
import me.pajic.toolpouch.compat.AccessoryUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** Binds a live child inventory to its original pouch, never to a newly preferred pouch. */
public final class PouchShulkerBinding {
    private final ServerPlayer player;
    private final ItemStack owner;
    private final int slot;
    private ItemStack expected;

    private PouchShulkerBinding(ServerPlayer player, ItemStack owner, int slot, ItemStack expected) {
        this.player = player;
        this.owner = owner;
        this.slot = slot;
        this.expected = expected.copy();
    }

    public static PouchShulkerBinding open(ServerPlayer player, ItemStack shulker, int ordinal) {
        ItemStack owner = ToolpouchAccess.multiShim$getToolPouch(player);
        if (owner.isEmpty() || ordinal < 0) return null;
        var contents = owner.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
        int found = 0;
        for (int i = 0; i < contents.size(); i++) {
            var child = contents.get(i);
            if (child.is(ItemTags.SHULKER_BOXES) && found++ == ordinal) {
                return ItemStack.matches(child, shulker) ? new PouchShulkerBinding(player, owner, i, child) : null;
            }
        }
        return null;
    }

    public boolean isOwner(ItemStack stack) { return stack == owner; }

    public boolean validFor(ServerPlayer user) {
        return user == player && ownsOriginal() && expectedChildPresent();
    }

    private boolean ownsOriginal() {
        if (owner.isEmpty()) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == owner) return true;
        }
        for (var equipment : EquipmentSlot.values()) {
            if (player.getItemBySlot(equipment) == owner) return true;
        }
        return AccessoryUtil.INSTANCE != null && AccessoryUtil.INSTANCE.tryGetToolPouch(player) == owner;
    }

    private boolean expectedChildPresent() {
        var contents = owner.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
        return slot < contents.size() && ItemStack.matches(contents.get(slot), expected);
    }

    public void save(ItemStack shulker, List<ItemStack> items) {
        if (!validFor(player)) return;
        var updated = expected.copy();
        updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        if (ItemStack.matches(updated, expected)) return;
        // Re-read the live parent so unrelated atlas, ammo, or other shulker updates survive.
        var contents = new ArrayList<>(owner.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList());
        contents.set(slot, updated);
        owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        shulker.set(DataComponents.CONTAINER, updated.get(DataComponents.CONTAINER));
        expected = updated.copy();
        player.getInventory().setChanged();
    }
}
