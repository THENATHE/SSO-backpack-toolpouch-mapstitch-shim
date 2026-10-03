package com.thenathe.mapstitchcompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import me.pajic.mapstitch.component.ModDataComponents;
import me.pajic.mapstitch.recipe.AtlasRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AtlasRecipe.class, remap = false)
public abstract class AtlasRecipeMixin {
    @Shadow private ItemStack map;
    @Shadow private int scale;
    // Integrated client/server matching can share the singleton recipe. Do not retain a world.
    @Unique private final ThreadLocal<WeakReference<Level>> multiShim$level = new ThreadLocal<>();

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"))
    private void multiShim$clearMatch(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        multiShim$level.set(new WeakReference<>(level));
        scale = -1;
        map = ItemStack.EMPTY;
    }

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("RETURN"), cancellable = true)
    private void multiShim$prepareSeed(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        var data = MapItem.getSavedData(map, level);
        if (data == null) { cir.setReturnValue(false); return; }
        // Crafting consumes one map even when the ingredient contains a stack of copies.
        map = map.copyWithCount(1);
        map.set(ModDataComponents.MAP_CENTER, new Vector2i(data.centerX, data.centerZ));
    }

    @WrapMethod(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;")
    private ItemStack multiShim$assembleCurrentInput(CraftingInput input, Operation<ItemStack> original) {
        var reference = multiShim$level.get();
        Level level = reference == null ? null : reference.get();
        // Rebuild the cached seed for each preview/craft; upstream tryInsert consumes its copy.
        if (level == null || !((AtlasRecipe)(Object)this).matches(input, level)) return ItemStack.EMPTY;
        return original.call(input);
    }
}
