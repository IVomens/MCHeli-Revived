package com.ivomens.mcheli.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Block names in the config, which may still use 1.12 names. */
public final class BlockNames {

    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow",
            "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** 1.12 block names that the flattening split or renamed, mapped to their 1.21 blocks. */
    private static final Map<String, List<String>> LEGACY_NAMES = Map.ofEntries(
            Map.entry("tallgrass", List.of("short_grass", "fern", "dead_bush")),
            Map.entry("double_plant", List.of("sunflower", "lilac", "tall_grass", "large_fern", "rose_bush",
                    "peony")),
            Map.entry("yellow_flower", List.of("dandelion")),
            Map.entry("red_flower", List.of("poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                    "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley")),
            Map.entry("reeds", List.of("sugar_cane")),
            Map.entry("waterlily", List.of("lily_pad")),
            Map.entry("torch", List.of("torch", "wall_torch", "soul_torch", "soul_wall_torch")),
            Map.entry("redstone_torch", List.of("redstone_torch", "redstone_wall_torch")),
            Map.entry("web", List.of("cobweb")),
            Map.entry("deadbush", List.of("dead_bush")),
            Map.entry("snow_layer", List.of("snow")),
            Map.entry("fence", List.of("oak_fence")),
            Map.entry("stonebrick", List.of("stone_bricks")),
            Map.entry("stained_glass_pane", COLORS.stream().map(c -> c + "_stained_glass_pane").toList()),
            Map.entry("stained_glass", COLORS.stream().map(c -> c + "_stained_glass").toList()));

    private BlockNames() {
    }

    /**
     * The blocks a name stands for. 1.12 names that were split or renamed resolve to all of their 1.21
     * blocks; names without a namespace default to {@code minecraft}; unknown names resolve to nothing.
     */
    public static List<Block> resolve(String blockName) {
        String name = blockName.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return List.of();
        }
        int colon = name.indexOf(':');
        String namespace = colon >= 0 ? name.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? name.substring(colon + 1) : name;
        List<String> paths = namespace.equals("minecraft") ? LEGACY_NAMES.getOrDefault(path, List.of(path))
                : List.of(path);
        List<Block> blocks = new ArrayList<>();
        for (String candidate : paths) {
            ResourceLocation id = ResourceLocation.tryBuild(namespace, candidate);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                blocks.add(BuiltInRegistries.BLOCK.get(id));
            }
        }
        return blocks;
    }

    /** The blocks of a comma separated list, without duplicates, in order. */
    static List<Block> resolveList(String commaSeparated) {
        return Stream.of(commaSeparated.split("\\s*,\\s*")).flatMap(name -> resolve(name).stream()).distinct()
                .toList();
    }
}
