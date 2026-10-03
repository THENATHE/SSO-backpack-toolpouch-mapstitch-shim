package shulkerqa;

import com.mojang.authlib.GameProfile;
import me.pajic.toolpouch.item.ModItems;
import me.pajic.toolpouch.network.NetworkEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Directed server-side boundary checks, separate from the real client's packet/accounting suite. */
public final class ShulkerServerBoundaryChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack box(String name) {
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(27);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal(name + " sword"));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(sword)));
        return box;
    }

    private static List<ItemStack> contents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).itemCopies().toList();
    }

    private static ItemStack seed(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        var owner = new ItemStack(ModItems.TOOL_POUCH);
        owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                List.of(ItemStack.EMPTY, box("Boundary A"), new ItemStack(Items.COMPASS))));
        player.getInventory().setItem(0, owner);
        return owner;
    }

    private static ShulkerBoxMenu open(ServerPlayer player) {
        NetworkEvents.openShulkerBox(player, 0);
        check(player.containerMenu instanceof ShulkerBoxMenu, "Expected portable shulker menu");
        return (ShulkerBoxMenu) player.containerMenu;
    }

    @SuppressWarnings("unchecked")
    public static List<String> run(MinecraftServer server) throws Exception {
        var results = new ArrayList<String>();
        var profile = new GameProfile(UUID.randomUUID(), "PouchBoundaryQA");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        // Only this disposable test connection bypasses negotiation; real-client coverage is separate.
        var field = Class.forName("com.thenathe.combinedshim.NativeClients").getDeclaredField("MODULES");
        field.setAccessible(true);
        ((PacketContextProvider) player.connection).getPacketContext().set(
                (PacketContext.Key<Set<String>>) field.get(null), Set.of("toolpouch"));
        try {
            var owner = seed(player);
            var original = owner.copy();
            for (int index : new int[]{-1, Integer.MAX_VALUE}) {
                NetworkEvents.openShulkerBox(player, index);
                check(player.containerMenu == player.inventoryMenu, "Invalid ordinal opened a menu");
                check(ItemStack.matches(owner, original), "Invalid ordinal modified owner");
                player.openMenu(new SimpleMenuProvider((id, inventory, user) ->
                        ChestMenu.threeRows(id, inventory, new SimpleContainer(27)), Component.literal("Boundary chest")));
                NetworkEvents.openShulkerBox(player, index);
                check(player.containerMenu == player.inventoryMenu, "Invalid ordinal did not safely close prior menu");
                check(ItemStack.matches(owner, original), "Invalid ordinal from another menu modified owner");
            }
            results.add("PASS negative/MAX ordinals from inventory and another open menu: no exception, no unintended open or data change");

            var menu = open(player);
            menu.getSlot(0).setByPlayer(ItemStack.EMPTY);
            check(contents(contents(owner).get(1)).isEmpty(), "Removal was not persisted immediately");
            check(contents(owner).get(0).isEmpty(), "Physical slot gap changed");
            var changed = new ArrayList<>(contents(owner));
            var other = changed.get(2);
            other.set(DataComponents.CUSTOM_NAME, Component.literal("Concurrent unrelated marker"));
            var expectedOther = other.copy();
            owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(changed));
            menu.getSlot(1).setByPlayer(new ItemStack(Items.BED.red()));
            player.closeContainer();
            check(ItemStack.matches(contents(owner).get(2), expectedOther), "Unrelated parent slot change overwritten");
            check(contents(contents(owner).get(1)).get(1).is(Items.BED.red()), "Child edit lost after unrelated parent update");
            results.add("PASS live removal, physical child index after an empty gap, subsequent insertion and unrelated parent-slot update preserved");

            owner = seed(player);
            menu = open(player);
            var replacement = box("Replacement");
            changed = new ArrayList<>(contents(owner));
            changed.set(1, replacement);
            owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(changed));
            check(!menu.stillValid(player), "Externally replaced child retained stale menu validity");
            player.closeContainer();
            check(ItemStack.matches(contents(owner).get(1), replacement), "Close overwrote externally replaced child");
            results.add("PASS external target-child replacement invalidates old session and survives close without stale overwrite");

            owner = seed(player);
            menu = open(player);
            var alternate = new ItemStack(ModItems.TOOL_POUCH);
            alternate.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(box("Alternate"))));
            var alternateBefore = alternate.copy();
            player.getInventory().setItem(1, alternate);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(8, owner);
            check(menu.stillValid(player), "Same-reference owner movement unnecessarily invalidated session");
            menu.getSlot(0).setByPlayer(ItemStack.EMPTY);
            player.closeContainer();
            check(contents(contents(owner).get(1)).isEmpty(), "Moved original owner missed child update");
            check(ItemStack.matches(alternate, alternateBefore), "Newly preferred pouch was overwritten");
            results.add("PASS same-reference owner reordering follows original owner and leaves newly preferred pouch untouched");

            seed(player);
            var leggings = new ItemStack(Items.IRON_LEGGINGS);
            leggings.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(box("Leggings"))));
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
            menu = open(player);
            check(leggings.getItem().canFitInsideContainerItems(), "Armor control must ordinarily fit inside a shulker");
            check(!menu.getSlot(0).mayPlace(leggings), "Exact owning armor can be nested inside its own child");
            check(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_LEGGINGS)), "Unrelated legal armor unexpectedly blocked");
            menu.getSlot(0).setByPlayer(ItemStack.EMPTY);
            player.closeContainer();
            check(contents(contents(leggings).get(0)).isEmpty(), "Equipped armor-backed owner missed live child update");
            results.add("PASS exact owning leggings rejected by actual shulker slot while unrelated armor stays legal; equipped owner updates persist");

            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            NetworkEvents.openShulkerBox(player, 0);
            check(player.containerMenu == player.inventoryMenu, "Missing owner opened invalid child menu");
            results.add("PASS opening with no available pouch/shulker safely ignored");
        } finally {
            player.closeContainer();
        }
        return results;
    }
}
