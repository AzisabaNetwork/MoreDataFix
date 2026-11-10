package net.azisaba.moredatafix.mixin;

import ca.spottedleaf.dataconverter.minecraft.versions.V2523;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(V2523.class)
public class MixinV2523 {
    @ModifyArg(method = "register", at = @At(value = "INVOKE", target = "Lca/spottedleaf/dataconverter/minecraft/converters/attributes/ConverterAbstractOldAttributesRename;register(ILjava/util/function/Function;)V"), index = 1)
    private static Function<String, String> register(Function<String, String> renamer) {
        return moredatafix$renameAttribute(renamer);
    }

    @Unique
    private static Function<String, String> moredatafix$renameAttribute(Function<String, String> originalRenamer) {
        return type -> {
            if (type.startsWith("minecraft:")) {
                return originalRenamer.apply(type.substring("minecraft:".length()));
            } else {
                return originalRenamer.apply(type);
            }
        };
    }
}
