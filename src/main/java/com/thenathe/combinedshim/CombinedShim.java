package com.thenathe.combinedshim;
import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;
public final class CombinedShim implements ModInitializer {
 public void onInitialize(){LoggerFactory.getLogger("SSO-backpack-toolpouch-mapstitch-shim").info("SSO_STACK_MODULES={}",Modules.enabled());}
}
