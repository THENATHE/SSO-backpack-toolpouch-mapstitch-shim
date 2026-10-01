package com.thenathe.ssopolymer.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.thenathe.ssopolymer.LootCompatibility;
import me.pajic.simple_smithing_overhaul.mixson.DataPatches;
import me.pajic.simple_smithing_overhaul.mixson.MixsonHelper;
import net.ramixin.mixson.util.Index;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataPatches.class, remap = false)
public abstract class DataPatchesMixin {
    @ModifyArg(method = {"lambda$init$2", "lambda$init$3"},
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonArray;add(Lcom/google/gson/JsonElement;)V"), index = 0)
    private static JsonElement ssoPolymer$templateLoot(JsonElement pool) {
        return LootCompatibility.preparePool(pool, false);
    }

    @ModifyArg(method = {"lambda$init$5", "lambda$init$7"},
            at = @At(value = "INVOKE", target = "Lcom/google/gson/JsonArray;add(Lcom/google/gson/JsonElement;)V"), index = 0)
    private static JsonElement ssoPolymer$configuredLoot(JsonElement pool) {
        return LootCompatibility.preparePool(pool, true);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private static void ssoPolymer$brokenAnvilLoot(CallbackInfo ci) {
        MixsonHelper.registerSingleJson("SSO Polymer: preserve broken anvil explosion conditions",
                new Index("simple_smithing_overhaul:loot_table/blocks/broken_anvil"), context -> {
                    JsonArray pools = context.getFile().getAsJsonObject().getAsJsonArray("pools");
                    for (int i = 0; i < pools.size(); i++) {
                        pools.set(i, LootCompatibility.preparePool(pools.get(i), false));
                    }
                });
    }
}
