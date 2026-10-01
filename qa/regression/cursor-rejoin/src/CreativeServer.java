package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
public class CreativeServer implements ModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));int stage=0,wait=0;boolean done=false;ItemStack[] original;
 void write(String n,String v)throws Exception{Files.writeString(control.resolve(n),v);}
 public void onInitialize(){ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{if(done&&Files.exists(control.resolve("intentional-disconnect"))){stage=0;wait=0;done=false;return;}if(!done)try{write("disconnect","Disconnected stage "+stage);}catch(Exception ignored){}});
 ServerTickEvents.END_SERVER_TICK.register(s->{if(done||s.getPlayerList().getPlayers().isEmpty())return;try{var p=s.getPlayerList().getPlayers().get(0);
 if(stage==0&&Files.exists(control.resolve("ready"))){p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
 var map=MapItem.create(p.level(),p.getBlockX(),p.getBlockZ(),(byte)0,true,false);
 var atlas=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mapstitch:atlas")));
 var pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.setDamageValue(780);
 var repaired=pick.copy();repaired.set(me.pajic.simple_smithing_overhaul.util.ModDataComponents.REPAIR_COUNT,1);
 original=new ItemStack[]{new ItemStack(Items.DIAMOND,16),new ItemStack(Items.MAP,3),map,atlas,pick,repaired};
 for(int i=0;i<original.length;i++)p.getInventory().setItem(i,original[i].copy());p.inventoryMenu.broadcastChanges();write("prepared","six items");stage=1;
 }
 if(stage==1&&Files.exists(control.resolve("finished"))&&++wait>20){
  if(!p.containerMenu.getCarried().isEmpty())throw new AssertionError("server cursor remains "+p.containerMenu.getCarried());
  for(int i=0;i<original.length;i++){var out=p.getInventory().getItem(9+i);if(!out.is(original[i].getItem())||out.getCount()!=original[i].getCount())throw new AssertionError("item mismatch "+i+" "+out);if(i==2&&!java.util.Objects.equals(out.get(DataComponents.MAP_ID),original[i].get(DataComponents.MAP_ID)))throw new AssertionError("map identity changed");if(i>=4&&(out.getDamageValue()!=original[i].getDamageValue()||!java.util.Objects.equals(out.get(me.pajic.simple_smithing_overhaul.util.ModDataComponents.REPAIR_COUNT),original[i].get(me.pajic.simple_smithing_overhaul.util.ModDataComponents.REPAIR_COUNT))))throw new AssertionError("tool damage/repair count changed");if(!p.getInventory().getItem(i).isEmpty())throw new AssertionError("old slot not empty "+i);}
  write("server-result","PASS: all six items moved exactly once, original identities/counts, map ID and tool damage/repair counts retained, cursor empty");done=true;
 }
 }catch(Throwable e){e.printStackTrace();try{write("server-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}
