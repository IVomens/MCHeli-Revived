package com.ivomens.mcheli.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Replacement for the block materials of 1.12, which 1.20 removed. Each constant keeps the old config
 * name and matches the blocks that used to have that material, using tags and block classes.
 */
public enum BlockMaterial {
    AIR("air", s -> s.isAir()),
    GRASS("grass", s -> s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL)),
    GROUND("ground", s -> s.is(BlockTags.DIRT) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)),
    WOOD("wood", s -> s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_SLABS)
            || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.FENCE_GATES)
            || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.WOODEN_BUTTONS)
            || s.is(BlockTags.WOODEN_PRESSURE_PLATES) || s.is(BlockTags.ALL_SIGNS) || s.is(Tags.WOODEN_CHESTS)
            || s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.BOOKSHELF) || s.is(Tags.WOODEN_BARRELS)),
    ROCK("rock", s -> s.is(BlockTags.MINEABLE_WITH_PICKAXE) && !s.is(BlockTags.ANVIL)
            && s.getSoundType() != SoundType.METAL && s.getSoundType() != SoundType.GLASS),
    IRON("iron", s -> s.getSoundType() == SoundType.METAL || s.is(Blocks.IRON_BARS)),
    ANVIL("anvil", s -> s.is(BlockTags.ANVIL)),
    WATER("water", s -> s.is(Blocks.WATER) || s.is(Blocks.BUBBLE_COLUMN)),
    LAVA("lava", s -> s.is(Blocks.LAVA)),
    LEAVES("leaves", s -> s.is(BlockTags.LEAVES)),
    PLANTS("plants", s -> s.getBlock() instanceof BushBlock || s.getBlock() instanceof SugarCaneBlock
            || s.getBlock() instanceof BambooStalkBlock || s.is(BlockTags.FLOWERS)),
    VINE("vine", s -> s.is(BlockTags.REPLACEABLE_BY_TREES) || s.getBlock() instanceof VineBlock
            || s.is(Blocks.GLOW_LICHEN)),
    SPONGE("sponge", s -> s.is(Blocks.SPONGE) || s.is(Blocks.WET_SPONGE)),
    CLOTH("cloth", s -> s.is(BlockTags.WOOL)),
    FIRE("fire", s -> s.is(BlockTags.FIRE)),
    SAND("sand", s -> s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SOUL_SAND)),
    CIRCUITS("circuits", s -> s.getBlock() instanceof RedStoneWireBlock || s.getBlock() instanceof DiodeBlock
            || s.getBlock() instanceof BaseRailBlock || s.getBlock() instanceof TorchBlock
            || s.getBlock() instanceof LeverBlock || s.getBlock() instanceof ButtonBlock
            || s.getBlock() instanceof FlowerPotBlock || s.getBlock() instanceof TripWireHookBlock
            || s.getBlock() instanceof TripWireBlock || s.is(Blocks.LADDER)),
    CARPET("carpet", s -> s.is(BlockTags.WOOL_CARPETS) || s.is(Blocks.MOSS_CARPET)),
    GLASS("glass", s -> s.is(Tags.GLASS_BLOCKS) || s.is(Tags.GLASS_PANES) || s.is(Blocks.GLOWSTONE)
            || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.BEACON)),
    REDSTONE_LIGHT("redstoneLight", s -> s.is(Blocks.REDSTONE_LAMP)),
    TNT("tnt", s -> s.is(Blocks.TNT)),
    CORAL("coral", s -> s.is(BlockTags.CORALS) || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.WALL_CORALS)),
    ICE("ice", s -> s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE)),
    PACKED_ICE("packedIce", s -> s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)),
    SNOW("snow", s -> s.is(Blocks.SNOW)),
    CRAFTED_SNOW("craftedSnow", s -> s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)),
    CACTUS("cactus", s -> s.is(Blocks.CACTUS)),
    CLAY("clay", s -> s.is(Blocks.CLAY)),
    GOURD("gourd", s -> s.is(Blocks.PUMPKIN) || s.is(Blocks.CARVED_PUMPKIN) || s.is(Blocks.JACK_O_LANTERN)
            || s.is(Blocks.MELON)),
    DRAGON_EGG("dragonEgg", s -> s.is(Blocks.DRAGON_EGG)),
    PORTAL("portal", s -> s.is(BlockTags.PORTALS)),
    CAKE("cake", s -> s.is(Blocks.CAKE) || s.is(BlockTags.CANDLE_CAKES)),
    WEB("web", s -> s.is(Blocks.COBWEB)),
    PISTON("piston", s -> s.getBlock() instanceof PistonBaseBlock || s.getBlock() instanceof PistonHeadBlock);

    public final String configName;
    private final Predicate<BlockState> predicate;

    BlockMaterial(String configName, Predicate<BlockState> predicate) {
        this.configName = configName;
        this.predicate = predicate;
    }

    public boolean matches(BlockState state) {
        return this.predicate.test(state);
    }

    public static Optional<BlockMaterial> fromName(String name) {
        String trimmed = name.trim();
        for (BlockMaterial material : values()) {
            if (material.configName.equalsIgnoreCase(trimmed)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }

    /** Shared "c" convention tags. A holder class, because enum constants cannot see their own statics. */
    private static final class Tags {

        static final TagKey<Block> WOODEN_CHESTS = common("chests/wooden");
        static final TagKey<Block> WOODEN_BARRELS = common("barrels/wooden");
        static final TagKey<Block> GLASS_BLOCKS = common("glass_blocks");
        static final TagKey<Block> GLASS_PANES = common("glass_panes");

        private static TagKey<Block> common(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
