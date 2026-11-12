package net.azisaba.moredatafix;

import ca.spottedleaf.dataconverter.converters.DataConverter;
import ca.spottedleaf.dataconverter.minecraft.MCVersions;
import ca.spottedleaf.dataconverter.types.MapType;
import ca.spottedleaf.dataconverter.types.nbt.NBTMapType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.bukkit.craftbukkit.util.CraftChatMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes the DataConverter write old items in the same form as the Azisaba plugins do.
 * <p>
 * AziSync, EnderChestPlus, StorageBox, ItemStash and SearchableInfiniteShop bring the items they convert from 1.15.2
 * to one form with their shared {@code ItemNormalizer}, so that the same item is the same data wherever it was kept.
 * Items in the world (chests, dropped items, item frames, mob equipment, playerdata) are converted by the server
 * itself, e.g. all at once with {@code --forceUpgrade}, and no plugin sees them. Left alone they keep their name and
 * lore as text with "§" codes and lose some hide flags, and then do not stack with the plugins' items.
 * <p>
 * This is the same normalization inside the DataConverter. It must stay in step with {@code ItemNormalizer}:
 * <ul>
 *   <li>a name or lore line stored as a bare string becomes the text component Bukkit builds from that string
 *       ({@code setDisplayName}/{@code setLore}), without trailing spaces;</li>
 *   <li>the 1.15.2 {@code HideFlags} are all kept. The stock converter drops the flag of a component the item does
 *       not have, e.g. "hide attributes" on a sword without custom attributes, which then shows its attack damage;</li>
 *   <li>an empty {@code minecraft:profile}, a {@code minecraft:dyed_color} of undyed leather and a
 *       {@code minecraft:block_entity_data} holding nothing but a position (left on 1.15.2 shields) are removed;</li>
 *   <li>the MythicMobs 4 {@code MYTHIC_TYPE} tag is removed when the item carries the same ID as
 *       {@code mythicmobs:type}.</li>
 * </ul>
 * Only Minecraft classes are used, no Bukkit server: {@code --forceUpgrade} runs before the server exists.
 */
public final class ItemNormalization {
    /** Carries the 1.15.2 HideFlags through the conversion; ends up in custom_data and is removed again at the end. */
    private static final String HIDE_FLAGS_MARKER = "MoreDataFixHideFlags";

    /** The last version with converters. Ours runs after every other item converter. */
    private static final int VERSION = MCVersions.V25W45A + 1;

    private static final String COMPONENTS = "components";
    private static final String CUSTOM_NAME = "minecraft:custom_name";
    private static final String LORE = "minecraft:lore";
    private static final String PROFILE = "minecraft:profile";
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final String DYED_COLOR = "minecraft:dyed_color";
    private static final String BLOCK_ENTITY_DATA = "minecraft:block_entity_data";
    private static final String TOOLTIP_DISPLAY = "minecraft:tooltip_display";
    private static final String PDC = "PublicBukkitValues";
    private static final int DEFAULT_LEATHER_COLOR = 0xA06540;
    private static final Set<String> POSITION_KEYS = Set.of("id", "x", "y", "z");

    /** What Bukkit hides for HIDE_ADDITIONAL_TOOLTIP (CraftMetaItem.HIDDEN_COMPONENTS_PREVIOUSLY). */
    private static final List<String> ADDITIONAL_TOOLTIP = List.of(
            "minecraft:banner_patterns", "minecraft:bees", "minecraft:block_entity_data", "minecraft:block_state",
            "minecraft:bundle_contents", "minecraft:charged_projectiles", "minecraft:container",
            "minecraft:container_loot", "minecraft:firework_explosion", "minecraft:fireworks", "minecraft:instrument",
            "minecraft:map_id", "minecraft:painting/variant", "minecraft:pot_decorations", "minecraft:potion_contents",
            "minecraft:tropical_fish/pattern", "minecraft:written_book_content", "minecraft:jukebox_playable");

    /** The components each 1.15.2 HideFlags bit hides, the way Bukkit stores item flags on 1.21.11. */
    private static final List<List<String>> HIDDEN_BY_FLAG = List.of(
            List.of("minecraft:enchantments"),
            List.of("minecraft:attribute_modifiers"),
            List.of("minecraft:unbreakable"),
            List.of("minecraft:can_break"),
            List.of("minecraft:can_place_on"),
            // Paper also hides stored enchantments on pre-1.20.5 items with this flag
            concat(ADDITIONAL_TOOLTIP, "minecraft:stored_enchantments"),
            List.of("minecraft:dyed_color"));

    private static final int CACHE_SIZE = 4096;
    private static final Tag UNCHANGED = new CompoundTag();
    private static final Map<String, Tag> NAME_CACHE = cache();
    private static final Map<String, Tag> LORE_CACHE = cache();

    private ItemNormalization() {}

    private static List<String> concat(List<String> list, String extra) {
        List<String> result = new java.util.ArrayList<>(list);
        result.add(extra);
        return List.copyOf(result);
    }

    private static Map<String, Tag> cache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Tag> eldest) {
                return size() > CACHE_SIZE;
            }
        });
    }

    /**
     * Called before an item is converted to components (1.20.5): remembers its HideFlags, which the conversion
     * consumes, in a tag that travels along in custom_data until {@link #normalize} applies it.
     *
     * @param input the item in the pre-1.20.5 format ({@code id}, {@code Count}, {@code tag})
     */
    public static void captureHideFlags(MapType input) {
        MapType tag = input.getMap("tag");
        if (tag == null) return;
        int flags = tag.getInt("HideFlags", 0);
        if (flags != 0) tag.setInt(HIDE_FLAGS_MARKER, flags);
    }

    /** The converter that normalizes every item once all other converters are done. */
    public static DataConverter<MapType, MapType> converter() {
        return new DataConverter<>(VERSION) {
            @Override
            public MapType convert(MapType data, long sourceVersion, long toVersion) {
                normalize(data);
                return null;
            }
        };
    }

    /**
     * Normalizes one item in place. Nested items (shulker boxes, bundles...) are items of their own to the
     * DataConverter and get their own call.
     *
     * @param item the item in the current format ({@code id}, {@code count}, {@code components})
     */
    public static void normalize(MapType item) {
        if (!(item instanceof NBTMapType nbt)) return;
        try {
            normalize(nbt.getTag());
        } catch (RuntimeException e) {
            // leave the item as the stock converter made it
        }
    }

    private static void normalize(CompoundTag item) {
        CompoundTag components = item.getCompound(COMPONENTS).orElse(null);
        if (components == null) return;

        if (components.get(CUSTOM_NAME) instanceof StringTag(String name)) {
            Tag fixed = bukkitText(name, false);
            if (fixed != null) components.put(CUSTOM_NAME, fixed);
        } else {
            trimTrailingSpaces(components.get(CUSTOM_NAME));
        }

        if (components.get(LORE) instanceof ListTag lore) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i) instanceof StringTag(String line)) {
                    Tag fixed = bukkitText(line, true);
                    if (fixed != null) lore.set(i, fixed);
                } else {
                    trimTrailingSpaces(lore.get(i));
                }
            }
        }

        if (components.get(PROFILE) instanceof CompoundTag profile && profile.isEmpty()) components.remove(PROFILE);

        // the stock converter turns the "hide dye" flag into an explicit default color, even on non-leather items
        if (components.get(DYED_COLOR) instanceof NumericTag dye && dye.intValue() == DEFAULT_LEATHER_COLOR) {
            components.remove(DYED_COLOR);
        }

        // a 1.15.2 shield kept its banner in a BlockEntityTag; once the colors are moved out only
        // {id:"minecraft:banner",x:0,y:0,z:0} is left, which says nothing
        if (components.get(BLOCK_ENTITY_DATA) instanceof CompoundTag blockEntity
                && POSITION_KEYS.containsAll(blockEntity.keySet())) {
            components.remove(BLOCK_ENTITY_DATA);
        }

        int hideFlags = 0;
        if (components.get(CUSTOM_DATA) instanceof CompoundTag customData) {
            if (customData.get(HIDE_FLAGS_MARKER) instanceof NumericTag marker) hideFlags = marker.intValue();
            customData.remove(HIDE_FLAGS_MARKER);

            if (customData.get("MYTHIC_TYPE") instanceof StringTag(String legacyId)
                    && customData.get(PDC) instanceof CompoundTag pdc
                    && pdc.get("mythicmobs:type") instanceof StringTag(String id)
                    && legacyId.trim().equals(id.trim())) {
                customData.remove("MYTHIC_TYPE");
            }
            if (customData.isEmpty()) components.remove(CUSTOM_DATA);
        }
        if (hideFlags != 0) hide(components, hideFlags);

        if (components.isEmpty()) item.remove(COMPONENTS);
    }

    /** Adds the components the 1.15.2 HideFlags hide to {@code minecraft:tooltip_display}. */
    private static void hide(CompoundTag components, int hideFlags) {
        CompoundTag tooltipDisplay = components.get(TOOLTIP_DISPLAY) instanceof CompoundTag existing ? existing : new CompoundTag();
        ListTag hidden = tooltipDisplay.get("hidden_components") instanceof ListTag existing ? existing : new ListTag();
        boolean changed = false;
        for (int bit = 0; bit < HIDDEN_BY_FLAG.size(); bit++) {
            if ((hideFlags & (1 << bit)) == 0) continue;
            for (String component : HIDDEN_BY_FLAG.get(bit)) {
                StringTag tag = StringTag.valueOf(component);
                if (hidden.contains(tag)) continue;
                hidden.add(tag);
                changed = true;
            }
        }
        if (!changed) return;
        tooltipDisplay.put("hidden_components", hidden);
        components.put(TOOLTIP_DISPLAY, tooltipDisplay);
    }

    /**
     * The text component Bukkit stores for a legacy ("§") string, as NBT. Null when it is that very string.
     */
    private static Tag bukkitText(String legacy, boolean lore) {
        Map<String, Tag> cache = lore ? LORE_CACHE : NAME_CACHE;
        Tag cached = cache.get(legacy);
        if (cached == null) {
            cached = UNCHANGED;
            // Bukkit's YAML reader loses trailing spaces (they are invisible), so they are dropped everywhere
            Component component = CraftChatMessage.fromStringOrNull(stripTrailingSpaces(legacy));
            // setLore stores an empty component for an empty line; setDisplayName removes the name (it is kept here)
            if (component == null && lore) component = Component.empty();
            if (component != null) {
                Tag built = ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component).result().orElse(null);
                if (built != null) {
                    trimTrailingSpaces(built);
                    if (!(built instanceof StringTag(String text) && text.equals(legacy))) cached = built;
                }
            }
            cache.put(legacy, cached);
        }
        return cached == UNCHANGED ? null : cached.copy();
    }

    /**
     * Drops the trailing spaces of a text component in the form Bukkit builds ({@code {extra:[...],text:""}}).
     * Bukkit's YAML reader loses them on some lines and keeps them on others, and they are invisible.
     */
    private static void trimTrailingSpaces(Tag component) {
        if (!(component instanceof CompoundTag map) || map.size() != 2
                || !(map.get("text") instanceof StringTag(String rootText)) || !rootText.isEmpty()
                || !(map.get("extra") instanceof ListTag extra)) return;
        while (!extra.isEmpty()) {
            int last = extra.size() - 1;
            Tag run = extra.get(last);
            CompoundTag styled = run instanceof CompoundTag compound ? compound : null;
            Tag textTag = styled == null ? run : styled.get("text");
            if (!(textTag instanceof StringTag(String text))) break;
            if (styled != null && (styled.contains("extra") || styled.contains("click_event") || styled.contains("hover_event"))) break;
            String trimmed = stripTrailingSpaces(text);
            if (trimmed.isEmpty()) {
                if (last == 0) break; // nothing but spaces: leave it
                extra.remove(last); // a run of spaces only (or an empty one) at the end
                continue;
            }
            if (trimmed.length() != text.length()) {
                if (styled == null) extra.set(last, StringTag.valueOf(trimmed));
                else styled.put("text", StringTag.valueOf(trimmed));
            }
            break;
        }
    }

    private static String stripTrailingSpaces(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') end--;
        return text.substring(0, end);
    }
}
