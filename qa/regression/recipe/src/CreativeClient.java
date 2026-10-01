package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.nio.file.*;
public class CreativeClient implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));int ticks=0,stage=0;boolean done=false;
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null)return;try{
  ticks++;
  if(stage==0&&ticks>100){c.getConnection().sendCommand("recipe give @s *");Files.writeString(control.resolve("request"),"all-recipes");stage=1;ticks=0;}
  else if(stage==1&&ticks>140){int count=c.player.getRecipeBook().getCollections().size();if(count<100)throw new AssertionError("too few recipe collections "+count);Files.writeString(control.resolve("client-result"),"PASS: received complete recipe unlock, recipe collections="+count+", remained connected7seconds");done=true;}
 }catch(Throwable t){t.printStackTrace();try{Files.writeString(control.resolve("client-result"),"FAIL: "+t);}catch(Exception ignored){}done=true;}});}
}
