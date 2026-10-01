package com.thenathe.combinedshim;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/** Explicit, operator-selected recovery; never rewrites inventories automatically. */
public final class RepairMaterialRecovery {
    private RepairMaterialRecovery() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("sso-shim")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("repair-held").executes(context -> {
                            var source = context.getSource();
                            var player = source.getPlayerOrException();
                            if (!restoreUnexpectedCalcite(player.getMainHandItem())) {
                                source.sendFailure(Component.literal("Held item has no unexpected calcite repair-material override to restore."));
                                return 0;
                            }
                            player.getInventory().setChanged();
                            player.inventoryMenu.broadcastFullState();
                            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastFullState();
                            source.sendSuccess(() -> Component.literal("Restored the held item's default repair material. Enchantments, damage and other components were preserved."), true);
                            return 1;
                        }))));
    }

    /** Only the known explicit override is eligible, and legitimate calcite defaults stay intact. */
    public static boolean restoreUnexpectedCalcite(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var explicit = stack.getComponentsPatch().split().added().get(DataComponents.REPAIRABLE);
        if (!onlyCalcite(explicit)) return false;
        var canonical = new ItemStack(stack.getItem()).get(DataComponents.REPAIRABLE);
        if (onlyCalcite(canonical)) return false;
        if (canonical == null) stack.remove(DataComponents.REPAIRABLE);
        else stack.set(DataComponents.REPAIRABLE, canonical);
        return true;
    }

    private static boolean onlyCalcite(Repairable repairable) {
        return repairable != null && repairable.items().size() == 1
                && repairable.items().contains(Items.CALCITE.builtInRegistryHolder());
    }
}
