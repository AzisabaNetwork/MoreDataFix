package net.azisaba.moredatafix.mixin;

import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import ca.spottedleaf.dataconverter.minecraft.versions.V4661;
import net.azisaba.moredatafix.ItemNormalization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V4661 is the last version with converters. Registering the item normalization there makes it the last thing done
 * to every item that is upgraded from an older version (see {@link ItemNormalization}).
 */
@Mixin(V4661.class)
public class MixinV4661 {
    @Inject(at = @At("TAIL"), method = "register")
    private static void registerItemNormalization(CallbackInfo ci) {
        MCTypeRegistry.ITEM_STACK.addStructureConverter(ItemNormalization.converter());
    }
}
