package com.thenathe.combinedshim;
import net.fabricmc.loader.api.FabricLoader;
import java.util.List;
public final class Modules {
 public static final List<String> IDS=List.of("mapstitch","simple_smithing_overhaul","tiered_backpacks","toolpouch");
 private Modules(){}
 public static boolean enabled(String mod){return IDS.contains(mod)&&FabricLoader.getInstance().isModLoaded(mod);}
 public static List<String> enabled(){return IDS.stream().filter(Modules::enabled).toList();}
}
