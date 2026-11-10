package net.azisaba.moredatafix.mixin;

import ca.spottedleaf.dataconverter.minecraft.converters.itemstack.ConverterItemStackToDataComponents;
import ca.spottedleaf.dataconverter.types.MapType;
import ca.spottedleaf.dataconverter.types.TypeUtil;
import com.google.common.collect.ImmutableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ConverterItemStackToDataComponents.class)
public class MixinConverterItemStackToDataComponents {
    @Unique
    private static final Map<String, String> moredatafix$renames = ImmutableMap.<String, String>builder()
            .put("generic.maxHealth", "minecraft:generic.max_health")
            .put("Max Health", "minecraft:generic.max_health")
            .put("zombie.spawnReinforcements", "minecraft:zombie.spawn_reinforcements")
            .put("Spawn Reinforcements Chance", "minecraft:zombie.spawn_reinforcements")
            .put("horse.jumpStrength", "minecraft:horse.jump_strength")
            .put("Jump Strength", "minecraft:horse.jump_strength")
            .put("generic.followRange", "minecraft:generic.follow_range")
            .put("Follow Range", "minecraft:generic.follow_range")
            .put("generic.knockbackResistance", "minecraft:generic.knockback_resistance")
            .put("Knockback Resistance", "minecraft:generic.knockback_resistance")
            .put("generic.movementSpeed", "minecraft:generic.movement_speed")
            .put("Movement Speed", "minecraft:generic.movement_speed")
            .put("generic.flyingSpeed", "minecraft:generic.flying_speed")
            .put("Flying Speed", "minecraft:generic.flying_speed")
            .put("generic.attackDamage", "minecraft:generic.attack_damage")
            .put("generic.attackKnockback", "minecraft:generic.attack_knockback")
            .put("generic.attackSpeed", "minecraft:generic.attack_speed")
            .put("generic.armorToughness", "minecraft:generic.armor_toughness")
            .build();

    @Inject(at = @At(value = "INVOKE", target = "Lca/spottedleaf/dataconverter/types/MapType;setString(Ljava/lang/String;Ljava/lang/String;)V"), method = "convertAttribute")
    private static void renameAttribute(Object inputGeneric, TypeUtil<?> type, CallbackInfoReturnable<MapType> cir) {
        if (inputGeneric instanceof MapType casted) {
            String oldType = casted.getString("AttributeName");
            String newType = moredatafix$renameAttribute(oldType);
            if (newType != null) {
                casted.setString("AttributeName", newType);
            }
        } else if (inputGeneric != null) {
            System.out.println("Unknown type encountered while converting attribute: " + inputGeneric + " (" + inputGeneric.getClass().getCanonicalName() + ")");
        }
    }

    @Unique
    private static String moredatafix$renameAttribute(String oldType) {
        if (oldType.startsWith("minecraft:")) {
            return moredatafix$renames.get(oldType.substring("minecraft:".length()));
        } else {
            return moredatafix$renames.get(oldType);
        }
    }
}
