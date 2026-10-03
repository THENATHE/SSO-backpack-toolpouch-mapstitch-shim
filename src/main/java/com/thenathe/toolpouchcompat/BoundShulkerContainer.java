package com.thenathe.toolpouchcompat;

import net.minecraft.world.item.ItemStack;

/** Used only by the vanilla shulker slots backed by Tool Pouch's portable container. */
public interface BoundShulkerContainer {
    boolean multiShim$isOwner(ItemStack stack);
}
