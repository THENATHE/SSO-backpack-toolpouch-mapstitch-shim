package qa;

import java.nio.file.*;
import me.pajic.simple_smithing_overhaul.items.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class NetworkServerQa implements ModInitializer {
    private int joined,stage,wait,actualCost,clickedTicks;
    private boolean setup,failed;
    private final Path control=Path.of(System.getProperty("sso.network.control"));
    private final String[] names={"free","expensive","smithing","grindstone"};
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private ItemStack sword(ServerPlayer p,int damage,int repairCost,boolean enchanted){
        ItemStack item=new ItemStack(Items.DIAMOND_SWORD);item.setDamageValue(damage);
        if(repairCost>0)item.set(DataComponents.REPAIR_COST,repairCost);
        if(enchanted)item.enchant(p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),1);
        return item;
    }
    private void begin(ServerPlayer player)throws Exception{
        player.closeContainer();player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);player.setExperienceLevels(stage==0?0:100);
        var level=player.level();var pos=BlockPos.containing(player.position()).below();
        if(stage<=1){
            level.setBlock(pos,Blocks.ANVIL.defaultBlockState(),3);
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new AnvilMenu(id,inventory,ContainerLevelAccess.create(level,pos)),Component.literal("SSO QA "+names[stage])));
            if(stage==0){player.containerMenu.getSlot(0).set(sword(player,900,0,false));player.containerMenu.getSlot(1).set(new ItemStack(Items.DIAMOND));}
            else{
                player.containerMenu.getSlot(0).set(sword(player,0,63,true));
                var book=new ItemStack(Items.ENCHANTED_BOOK);
                EnchantmentHelper.updateEnchantments(book,e->e.set(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),2));
                player.containerMenu.getSlot(1).set(book);
            }
            ((AnvilMenu)player.containerMenu).createResult();actualCost=((AnvilMenu)player.containerMenu).getCost();
            check(stage==0?actualCost==0:actualCost>=40,"Unexpected actual anvil cost "+actualCost);
        }else if(stage==2){
            level.setBlock(pos,Blocks.SMITHING_TABLE.defaultBlockState(),3);
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new SmithingMenu(id,inventory,ContainerLevelAccess.create(level,pos)),Component.literal("SSO QA smithing")));
            player.getInventory().setItem(0,new ItemStack(ModItems.ENCHANTMENT_UPGRADE_SMITHING_TEMPLATE));
            player.getInventory().setItem(1,sword(player,0,0,true));
            player.getInventory().setItem(2,new ItemStack(Items.LAPIS_LAZULI));
            actualCost=me.pajic.simple_smithing_overhaul.SSO.CONFIG.enchantmentUpgrading.upgradingBaseExperienceCost.get();
        }else{
            actualCost=0;
            level.setBlock(pos,Blocks.GRINDSTONE.defaultBlockState(),3);
            player.openMenu(new SimpleMenuProvider((id,inventory,p)->new GrindstoneMenu(id,inventory,ContainerLevelAccess.create(level,pos)),Component.literal("SSO QA grindstone")));
            player.containerMenu.getSlot(0).set(sword(player,0,31,true));
            player.getInventory().setItem(0,new ItemStack(Items.NETHERITE_SCRAP));
        }
        player.containerMenu.broadcastChanges();
        int result=stage==2?3:2;if(stage<=1)check(player.containerMenu.getSlot(result).hasItem(),"No server output "+names[stage]);
        Files.writeString(control.resolve("command"),names[stage]+":"+result);setup=true;wait=0;clickedTicks=0;
    }
    private void verify(ServerPlayer player)throws Exception{
        ItemStack output=player.containerMenu.getCarried();check(!output.isEmpty(),"No carried result "+names[stage]);
        if(stage==0){check(output.getDamageValue()<900,"free repair did not repair");check(player.experienceLevel==0,"free repair changed XP");}
        if(stage==1){check(player.experienceLevel==100-actualCost,"expensive anvil wrong XP");}
        if(stage==2){
            check(EnchantmentHelper.getItemEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),output)==2,"smithing level unchanged");
            check(player.experienceLevel==100-actualCost,"smithing wrong XP");
            check(player.containerMenu.getSlot(0).getItem().isEmpty()&&player.containerMenu.getSlot(2).getItem().isEmpty(),"smithing ingredients not consumed");
        }
        if(stage==3){check(output.getOrDefault(DataComponents.REPAIR_COST,0)==15,"grindstone repair cost not halved");check(output.isEnchanted(),"grindstone stripped enchantments");check(player.experienceLevel==100,"grindstone awarded XP");}
        Files.writeString(control.resolve("server-evidence.txt"),"PASS "+names[stage]+" realCost="+actualCost+" xp="+player.experienceLevel+" result="+output+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        stage++;setup=false;wait=0;
        if(stage==names.length){player.closeContainer();Files.writeString(control.resolve("network-result.txt"),"PASS all four real client click transactions\n");}
    }
    @Override public void onInitialize(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(failed)return;
            try{
                for(ServerPlayer p:server.getPlayerList().getPlayers()){
                    if(p.getGameProfile().name().equals("StitchVanillaQA")){
                        if(++joined==60)Files.writeString(control.resolve("vanilla-joined"),"PASS official vanilla client connected and ticked for 60 ticks\n");
                    }else if(p.getGameProfile().name().equals("StitchNativeQA")&&stage<names.length){
                        if(!setup){if(++wait>40)begin(p);}
                        else{
                            Path clicked=control.resolve("clicked");
                            if(Files.exists(clicked)&&Files.readString(clicked).trim().startsWith(names[stage]+":")){
                                if(++clickedTicks>20)verify(p);
                            }else if(++wait>500)throw new AssertionError("Timeout waiting for click "+names[stage]);
                        }
                    }
                }
            }catch(Throwable error){failed=true;error.printStackTrace();try{Files.writeString(control.resolve("server-failure"),error.toString());}catch(Exception ignored){}}
        });
    }
}
