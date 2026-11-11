package net.azisaba.moredatafix.mixin;

import ca.spottedleaf.dataconverter.minecraft.versions.V3945;
import ca.spottedleaf.dataconverter.types.MapType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * V3945 converts attribute modifier UUIDs into resource-location ids ({@code minecraft:<uuid>})
 * and collects them in a map keyed by that id. MythicMobs assigns the same UUID (derived from the
 * item's internal name) to every attribute modifier of an item, so all modifiers except the last
 * one get overwritten and disappear during the upgrade.
 * <p>
 * This redirects the {@code ret.put(id, modifier)} call of the fallback branch (unknown UUID) and
 * assigns a unique id when the id is already taken.
 */
@Mixin(V3945.class)
public class MixinV3945 {
    @Redirect(
            method = "remapModifiers",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 2)
    )
    private static Object moredatafix$putUnique(Map<Object, Object> ret, Object key, Object value) {
        if (!ret.containsKey(key)) {
            return ret.put(key, value);
        }

        String baseId = (String) key;
        String uniqueId;
        int i = 1;
        do {
            uniqueId = baseId + "_" + i++;
        } while (ret.containsKey(uniqueId));

        if (value instanceof MapType modifier) {
            modifier.setString("id", uniqueId);
        }
        return ret.put(uniqueId, value);
    }
}
