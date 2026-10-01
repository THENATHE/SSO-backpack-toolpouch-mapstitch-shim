package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Player;
import java.nio.file.*;
public class CreativeClient implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));int stage=0,ticks=0,item=0;boolean done=false;CreativeClientScreen screen;
 void write(String n,String v)throws Exception{Files.writeString(control.resolve(n),v);}
 void trace(String v)throws Exception{Files.writeString(control.resolve("client-observations"),v+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 public static class CreativeClientScreen extends InventoryScreen {
  public CreativeClientScreen(Player p){super(p);}
  String click(int slot){var s=getMenu().getSlot(slot);var event=new MouseButtonEvent(leftPos+s.x+8,topPos+s.y+8,new MouseButtonInfo(1,0));boolean down=mouseClicked(event,false);boolean up=mouseReleased(event);return "slot="+slot+" xy="+event.x()+","+event.y()+" size="+width+","+height+" accepted="+down+","+up+" slotAfter="+getMenu().getSlot(slot).getItem();}
 }
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null)return;try{ticks++;var m=c.player.containerMenu;
 if(stage==0&&ticks>80){screen=new CreativeClientScreen(c.player);c.setScreenAndShow(screen);write("ready","yes");stage=1;ticks=0;}
 else if(stage==1&&Files.exists(control.resolve("prepared"))&&ticks>60){stage=2;ticks=0;}
 else if(stage==2&&ticks>10){if(!m.getCarried().isEmpty())throw new AssertionError("cursor not empty before item "+item);trace("mouse "+screen.click(36+item));trace("pickup "+item+" cursor="+m.getCarried());stage=3;ticks=0;}
 else if(stage==3&&ticks>Integer.getInteger("cursor.qa.clickDelay",20)){if(m.getCarried().isEmpty())throw new AssertionError("pickup not held "+item);trace("mouse "+screen.click(9+item));trace("place "+item+" immediate cursor="+m.getCarried()+" target="+m.getSlot(9+item).getItem());stage=4;ticks=0;}
 else if(stage==4){if(ticks==30)net.minecraft.client.Screenshot.grab(c,false);trace("after-place item="+item+" tick="+ticks+" cursor="+m.getCarried()+" target="+m.getSlot(9+item).getItem());if(ticks>40){if(!m.getCarried().isEmpty())throw new AssertionError("GHOST cursor after place item="+item+" cursor="+m.getCarried());if(m.getSlot(9+item).getItem().isEmpty())throw new AssertionError("target empty item="+item);item++;ticks=0;if(item==6){write("finished","yes");stage=5;}else stage=2;}}
 else if(stage==5&&ticks>40&&Files.exists(control.resolve("server-result"))){var result=Files.readString(control.resolve("server-result"));if(!result.startsWith("PASS"))throw new AssertionError(result);write("client-result","PASS: actual screen mouse pickup/place of six item types; empty client cursor after each place; server verified exact moved stacks");done=true;}
 }catch(Throwable e){e.printStackTrace();try{write("client-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}
