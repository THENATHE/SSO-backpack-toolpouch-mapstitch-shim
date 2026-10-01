package qa;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import java.util.*;

/** Executes the real recovery command; verifies selected-stack-only, exact component preservation. */
public final class RepairHeldChecks {
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    public static List<String> run(MinecraftServer server)throws Exception{
        var events=new ArrayList<String>();var profile=new GameProfile(UUID.randomUUID(),"RecoveryQA");
        var p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(profile,false));
        p.getInventory().setSelectedSlot(0);
        var source=server.createCommandSourceStack().withEntity(p).withPermission(LevelBasedPermissionSet.OWNER);
        var calcite=new Repairable(HolderSet.direct(Items.CALCITE.builtInRegistryHolder()));
        var broken=new ItemStack(Items.DIAMOND_PICKAXE);broken.setDamageValue(3);
        broken.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),1);
        broken.set(DataComponents.CUSTOM_NAME,Component.literal("Recovery component retention"));broken.set(DataComponents.REPAIRABLE,calcite);
        var expected=broken.copy();expected.set(DataComponents.REPAIRABLE,new ItemStack(Items.DIAMOND_PICKAXE).get(DataComponents.REPAIRABLE));
        var other=broken.copy();p.getInventory().setItem(1,other.copy());p.getInventory().setSelectedItem(broken.copy());
        server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(ItemStack.matches(expected,p.getMainHandItem()),"Recovery changed unrelated components or failed canonical repair restore");
        check(ItemStack.matches(other,p.getInventory().getItem(1)),"Recovery modified unselected item");
        events.add("PASS OP recovery restores canonical diamond repair material; damage3/EfficiencyI/name/count preserved; other inventory item untouched");
        var stone=new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("simple_smithing_overhaul:whetstone")));
        stone.setDamageValue(2);var enchantments=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),1);
        stone.set(DataComponents.STORED_ENCHANTMENTS,enchantments.toImmutable());var stoneExpected=stone.copy();stone.set(DataComponents.REPAIRABLE,calcite);
        p.getInventory().setSelectedItem(stone);server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(ItemStack.matches(stoneExpected,p.getMainHandItem()),"Whetstone recovery changed damage/stored enchantments or failed material restore");
        events.add("PASS corrupted whetstone restores canonical quartz material; damage2/storedEfficiencyI/count preserved");
        var clean=expected.copy();p.getInventory().setSelectedItem(clean.copy());
        server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(ItemStack.matches(clean,p.getMainHandItem()),"Clean item changed");events.add("PASS clean item unchanged");
        ItemStack chalk=null;
        for(var item:BuiltInRegistries.ITEM){var stack=new ItemStack(item);var repair=stack.get(DataComponents.REPAIRABLE);
            if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("chalk")&&repair!=null&&repair.isValidRepairItem(new ItemStack(Items.CALCITE))){chalk=stack;break;}}
        check(chalk!=null,"No legitimately calcite-repairable Chalk control");
        p.getInventory().setSelectedItem(chalk.copy());server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(ItemStack.matches(chalk,p.getMainHandItem()),"Legitimate calcite-repairable Chalk changed");events.add("PASS legitimate calcite-repairable Chalk unchanged");
        var custom=expected.copy();custom.set(DataComponents.REPAIRABLE,new Repairable(HolderSet.direct(Items.IRON_INGOT.builtInRegistryHolder())));
        p.getInventory().setSelectedItem(custom.copy());server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(ItemStack.matches(custom,p.getMainHandItem()),"Intentional non-calcite override changed");events.add("PASS explicit non-calcite repair override unchanged");
        p.getInventory().setSelectedItem(broken.copy());boolean denied=false;
        try{server.getCommands().getDispatcher().execute("sso-shim repair-held",source.withPermission(LevelBasedPermissionSet.ALL));}catch(CommandSyntaxException e){denied=true;}
        check(denied&&ItemStack.matches(broken,p.getMainHandItem()),"Non-OP recovery was not rejected unchanged");events.add("PASS non-OP command rejected; item unchanged");
        p.getInventory().setSelectedItem(ItemStack.EMPTY);server.getCommands().getDispatcher().execute("sso-shim repair-held",source);
        check(p.getMainHandItem().isEmpty(),"Empty hand changed");events.add("PASS empty hand unchanged");
        return events;
    }
}
