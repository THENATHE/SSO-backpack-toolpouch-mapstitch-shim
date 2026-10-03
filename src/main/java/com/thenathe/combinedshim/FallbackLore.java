package com.thenathe.combinedshim;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Adds a wire-only compatibility hint without discarding lore or exceeding its codec limit. */
public final class FallbackLore {
    private FallbackLore() {}

    public static ItemLore withNotice(ItemLore original, Component notice) {
        var lines = new ArrayList<>(original.lines());
        lines.add(notice);
        fitTooltip(lines);
        return new ItemLore(lines);
    }

    /** Polymer adds container/dye/enchantment lines before constructing its wire lore. */
    public static void fitTooltip(List<Component> lines) {
        if (lines.size() <= ItemLore.MAX_LINES) return;
        int last = ItemLore.MAX_LINES - 1;
        var combined = Component.empty().append(lines.get(last));
        for (int i = last + 1; i < lines.size(); i++) combined.append("\n").append(lines.get(i));
        lines.subList(last, lines.size()).clear();
        lines.add(combined);
    }
}
