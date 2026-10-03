package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.nio.file.*;
import java.util.*;
public class CreativeClient implements ClientModInitializer {
    Path control=Path.of(System.getProperty("creative.qa.control"));
    int ticks=0,stage=0,n=0,wait=0; boolean done=false;
    String[] items={"tiered_backpacks:leather_backpack","tiered_backpacks:copper_backpack","tiered_backpacks:iron_backpack","tiered_backpacks:golden_backpack","tiered_backpacks:diamond_backpack","tiered_backpacks:netherite_backpack"};
    void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
    boolean ack(String text)throws Exception{return Files.exists(control.resolve("ack"))&&Files.readString(control.resolve("ack")).equals(text);}
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null||c.level==null)return;try{
        ticks++;wait++;if(wait>500)throw new AssertionError("timeout n="+n+" stage="+stage+" dimension="+c.level.dimension().identifier()+" item="+c.player.getInventory().getItem(0));
        // Six tiers x four dimensions x Creative non-op / Creative op / Survival op give.
        int group=n/items.length, dim=group%4, mode=group/4;
        String id=items[n%items.length], key="case "+n+" "+id;
        switch(stage){
            case 0->{if(ticks<80)return;write("request","setup "+group);stage=1;wait=0;}
            case 1->{if(ack("setup "+group)&&wait>15
                && c.level.dimension().identifier().toString().equals(new String[]{"minecraft:overworld","minecraft:the_nether","minecraft:the_end","qa:extra"}[dim])
                && c.player.getInventory().getItem(0).isEmpty()) {stage=2;wait=0;}}
            case 2->{if(mode<2){
                ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
                if(stack.isEmpty())throw new AssertionError("missing native item "+id);
                stack.set(DataComponents.CUSTOM_NAME,Component.literal("creative-regression-"+n));
                stack.set(DataComponents.DYED_COLOR,new DyedItemColor(0x123456));
                stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7))));
                c.gameMode.handleCreativeModeItemAdd(stack,36);
                write("request",key);stage=3;wait=0;
            }else{c.getConnection().sendCommand("give @s "+id);write("request",key);stage=3;wait=0;}}
            case 3->{if(ack(key)&&wait>8){
                var stack=c.player.getInventory().getItem(0);
                // File acknowledgements can arrive before the client processes inventory packets,
                // especially while entering a dimension. Await observed state with the bounded timeout.
                if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id)) return;
                if(mode<2 && !stack.getHoverName().getString().equals("creative-regression-"+n)) return;
                if(mode<2 && (stack.get(DataComponents.CONTAINER)==null||stack.get(DataComponents.CONTAINER).itemCopies().noneMatch(s->s.is(Items.DIAMOND)&&s.getCount()==7)))throw new AssertionError("client contents lost "+n);
                write("client-progress",key);n++;
                if(n==72){write("client-result","PASS 48 Creative item packets (op and non-op) + 24 player-issued /give commands across Overworld, Nether, End, custom dimension");done=true;}
                else{stage=0;wait=0;}
            }}
        }
    }catch(Throwable t){t.printStackTrace();try{write("client-result","FAIL "+t);}catch(Exception ignored){}done=true;}});}
}
