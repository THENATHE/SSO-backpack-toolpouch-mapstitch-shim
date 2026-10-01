package mending;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.InteractionHand;
import me.pajic.simple_smithing_overhaul.util.ModDataComponents;
public class MendingClientQa implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("mending.qa.control"));String current="";int ticks;boolean failed;
 void write(String f,String s)throws Exception{Files.writeString(control.resolve(f),s);}
 void log(String s)throws Exception{Files.writeString(control.resolve("client-evidence.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(failed||c.player==null||c.level==null)return;try{
 if(Boolean.getBoolean("mending.isolate-broken"))me.pajic.simple_smithing_overhaul.SSO.CONFIG.mendingRework.autoRepairOnBreak.accept(false);if(!Files.exists(control.resolve("command")))return;String next=Files.readString(control.resolve("command"));if(!next.equals(current)){current=next;ticks=0;c.gui.setScreen(null);c.options.keyShift.setDown(!current.equals("no-crouch"));c.player.getInventory().setSelectedSlot(0);}
 ticks++;if(ticks==40){log("PRE_USE "+current+" damage="+c.player.getMainHandItem().getDamageValue()+" broken="+me.pajic.simple_smithing_overhaul.util.ModUtil.isBroken(c.player.getMainHandItem()));var result=c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);log("USE "+current+" shift="+c.player.isShiftKeyDown()+" response="+result);if(current.equals("repeated")){log("REPEAT "+c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND));log("REPEAT "+c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND));}}
 if(ticks==65)write("ack",current);
 if(ticks>70&&Files.exists(control.resolve("snapshot-case"))&&Files.readString(control.resolve("snapshot-case")).equals(current)&&!Files.exists(control.resolve("checked"))){var t=c.player.getInventory().getItem(0);String observed=t.getDamageValue()+":"+c.player.getInventory().getItem(2).getCount()+":"+t.getOrDefault(ModDataComponents.REPAIR_COUNT,0)+":"+c.player.getInventory().getItem(1).getDamageValue();String server=Files.readString(control.resolve("snapshot"));log("SYNC "+current+" client="+observed+" server="+server+" equal="+observed.equals(server));write("checked",current);}
 }catch(Throwable failure){failed=true;failure.printStackTrace();try{write("result.txt","FAIL client "+failure);}catch(Exception ignored){}}});}
}
