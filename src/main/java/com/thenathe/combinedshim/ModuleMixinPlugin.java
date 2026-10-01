package com.thenathe.combinedshim;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
/** Gate before target loading: absent originals never load their compatibility classes. */
public final class ModuleMixinPlugin implements IMixinConfigPlugin {
 private static final Map<String,String> MODULES=Map.of("ssopolymer","simple_smithing_overhaul","backpackcompat","tiered_backpacks","toolpouchcompat","toolpouch","mapstitchcompat","mapstitch");
 public void onLoad(String p){}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(mixin.equals("com.thenathe.ssopolymer.mixin.DefaultedSyncMixin")&&!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("defaulted"))return false;for(var e:MODULES.entrySet())if(mixin.startsWith("com.thenathe."+e.getKey()+".mixin."))return Modules.enabled(e.getValue());return true;}
 public void acceptTargets(Set<String> mine,Set<String> others){}
 public List<String> getMixins(){return null;}
 public void preApply(String n,ClassNode c,String m,IMixinInfo i){}
 public void postApply(String n,ClassNode c,String m,IMixinInfo i){}
}
