package qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import me.pajic.simple_smithing_overhaul.items.ModItems;
import me.pajic.simple_smithing_overhaul.recipe.PortableItemRepairRecipe;
import me.pajic.simple_smithing_overhaul.util.ModUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Reconstructs only the reported three item values, never loads player/world data. */
public final class UserStateQa implements ModInitializer {
    final Path control=Path.of(System.getProperty("userstate.qa.control"));
    public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(this::run);}
    private Map<String,Object> summary(ItemStack stack){
        var out=new LinkedHashMap<String,Object>();out.put("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("count",stack.getCount());out.put("damage",stack.getDamageValue());out.put("max_damage",stack.getMaxDamage());
        out.put("repairable",String.valueOf(stack.get(DataComponents.REPAIRABLE)));out.put("components",stack.getComponentsPatch().toString());return out;
    }
    private void run(MinecraftServer server){
        var report=new LinkedHashMap<String,Object>();
        try{
            var recipeKey=ResourceKey.<Recipe<?>>create(Registries.RECIPE,Identifier.parse("simple_smithing_overhaul:whetstone_repair_item"));
            report.put("recipe_present",server.getRecipeManager().byKey(recipeKey).isPresent());
            report.put("diamond_tags",Items.DIAMOND.builtInRegistryHolder().tags().map(Object::toString).toList());
            report.put("pickaxe_tags",Items.DIAMOND_PICKAXE.builtInRegistryHolder().tags().map(Object::toString).toList());
            report.put("diamond_in_chalk_tag",new ItemStack(Items.DIAMOND).is(TagKey.create(Registries.ITEM,Identifier.parse("chalk:chalks"))));
            report.put("pickaxe_in_chalk_tag",new ItemStack(Items.DIAMOND_PICKAXE).is(TagKey.create(Registries.ITEM,Identifier.parse("chalk:chalks"))));
            report.put("default_pickaxe",summary(new ItemStack(Items.DIAMOND_PICKAXE)));
            var profile=new GameProfile(UUID.randomUUID(),"SSOStateQA");
            var player=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
            player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(profile,false));
            var cases=new ArrayList<Map<String,Object>>();
            for(boolean enchanted:new boolean[]{true,false})for(boolean targetPatch:new boolean[]{true,false})
            for(boolean calcitePatch:new boolean[]{true,false})for(int count:new int[]{1,9}){
                var pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.setDamageValue(3);
                var stone=new ItemStack(ModItems.WHETSTONE);
                if(targetPatch)pick.set(DataComponents.REPAIRABLE,new Repairable(HolderSet.direct(Items.CALCITE.builtInRegistryHolder())));
                if(enchanted){
                    var efficiency=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
                    pick.enchant(efficiency,1);var stored=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);stored.set(efficiency,1);
                    stone.set(DataComponents.STORED_ENCHANTMENTS,stored.toImmutable());
                }
                var diamonds=new ItemStack(Items.DIAMOND,count);
                if(calcitePatch)diamonds.set(DataComponents.REPAIRABLE,new Repairable(HolderSet.direct(Items.CALCITE.builtInRegistryHolder())));
                var c=new LinkedHashMap<String,Object>();c.put("enchanted",enchanted);c.put("calcite_patch_on_target",targetPatch);c.put("calcite_patch_on_diamond",calcitePatch);c.put("diamond_count",count);
                c.put("pickaxe",summary(pick));c.put("whetstone",summary(stone));c.put("diamonds",summary(diamonds));
                c.put("unit_cost",ModUtil.determineUnitCost(pick));c.put("valid_diamond_material",ModUtil.isValidRepairItem(pick,diamonds));
                c.put("valid_calcite_material",ModUtil.isValidRepairItem(pick,new ItemStack(Items.CALCITE)));
                var input=CraftingInput.of(3,1,List.of(pick,diamonds,stone));
                var direct=new PortableItemRepairRecipe();boolean directMatch=direct.matches(input,server.overworld());c.put("direct_matches",directMatch);
                if(directMatch)c.put("direct_output",summary(direct.assemble(input)));
                var found=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,server.overworld());c.put("manager_matches",found.isPresent());
                if(found.isPresent()){c.put("manager_recipe",found.get().id().toString());c.put("manager_output",summary(found.get().value().assemble(input)));}
                var inventory=player.inventoryMenu;for(int i=1;i<=4;i++)inventory.getSlot(i).set(ItemStack.EMPTY);
                inventory.getSlot(1).set(pick.copy());inventory.getSlot(2).set(diamonds.copy());inventory.getSlot(3).set(stone.copy());
                c.put("inventory_output",summary(inventory.getSlot(0).getItem()));
                var pos=new BlockPos(0,100,0);server.overworld().setBlock(pos,Blocks.CRAFTING_TABLE.defaultBlockState(),2);
                var table=new CraftingMenu(1,player.getInventory(),ContainerLevelAccess.create(server.overworld(),pos));
                table.getSlot(1).set(pick.copy());table.getSlot(2).set(diamonds.copy());table.getSlot(3).set(stone.copy());
                c.put("table_output",summary(table.getSlot(0).getItem()));cases.add(c);
            }
            report.put("cases",cases);
            if(Boolean.getBoolean("userstate.qa.recovery"))report.put("recovery",RepairHeldChecks.run(server));
            report.put("completed",true);
        }catch(Throwable error){error.printStackTrace();report.put("completed",false);report.put("failure",error.toString());}
        try{Files.writeString(control.resolve("observations.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}
    }
}
