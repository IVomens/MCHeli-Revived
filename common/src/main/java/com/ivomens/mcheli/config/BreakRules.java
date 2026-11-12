package com.ivomens.mcheli.config;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.stream.Stream;

/**
 * Which blocks bullets shoot through and which blocks ground vehicles drive through. Built once per
 * config snapshot from the block and material lists of the file.
 */
public record BreakRules(List<Block> bulletBreakable, VehicleBreakRules car, VehicleBreakRules tank) {

    public static final StringOption BULLET_BREAKABLE_BLOCKS = StringOption.of("BulletBreakableBlocks",
            "glass_pane, stained_glass_pane, tallgrass, double_plant, yellow_flower, red_flower, vine, wheat, reeds,"
                    + " waterlily").since("0.10.4");
    public static final StringOption CAR_BREAKABLE_BLOCKS = StringOption.of("Collision_Car_BreakableBlock",
            "double_plant, glass_pane,stained_glass_pane");
    public static final StringOption CAR_BREAKABLE_MATERIALS = StringOption.of("Collision_Car_BreakableMaterial",
            "cactus, cake, gourd, leaves, vine, plants");
    public static final StringOption TANK_BREAKABLE_BLOCKS = StringOption.of("Collision_Tank_BreakableBlock",
            "nether_brick_fence").since("1.0.0");
    public static final StringOption TANK_BREAKABLE_MATERIALS = StringOption.of("Collision_Tank_BreakableMaterial",
            "cactus, cake, carpet, circuits, glass, gourd, leaves, vine, wood, plants");
    /** Blocks that stop a vehicle's path. Not in the file; these are the values older versions shipped. */
    private static final String CAR_UNBREAKABLE_BLOCKS = "torch";
    private static final String TANK_UNBREAKABLE_BLOCKS = "torch, glowstone";

    /** Weight types of {@code VehicleSpec}. */
    private static final int WEIGHT_CAR = 1;
    private static final int WEIGHT_TANK = 2;

    public BreakRules {
        bulletBreakable = List.copyOf(bulletBreakable);
    }

    static BreakRules from(ConfigValues v) {
        return new BreakRules(BlockNames.resolveList(v.get(BULLET_BREAKABLE_BLOCKS)),
                VehicleBreakRules.of(v.get(CAR_BREAKABLE_BLOCKS), CAR_UNBREAKABLE_BLOCKS,
                        v.get(CAR_BREAKABLE_MATERIALS)),
                VehicleBreakRules.of(v.get(TANK_BREAKABLE_BLOCKS), TANK_UNBREAKABLE_BLOCKS,
                        v.get(TANK_BREAKABLE_MATERIALS)));
    }

    /** The rules for a vehicle weight type; anything but a car or a tank breaks nothing. */
    public VehicleBreakRules forWeightType(int weightType) {
        return switch (weightType) {
            case WEIGHT_CAR -> this.car;
            case WEIGHT_TANK -> this.tank;
            default -> VehicleBreakRules.NONE;
        };
    }

    /**
     * What one kind of vehicle breaks: listed blocks always, listed materials unless the block is in
     * the unbreakable list.
     */
    public record VehicleBreakRules(List<Block> breakable, List<Block> unbreakable, List<BlockMaterial> materials) {

        public static final VehicleBreakRules NONE = new VehicleBreakRules(List.of(), List.of(), List.of());

        public VehicleBreakRules {
            breakable = List.copyOf(breakable);
            unbreakable = List.copyOf(unbreakable);
            materials = List.copyOf(materials);
        }

        static VehicleBreakRules of(String breakable, String unbreakable, String materials) {
            return new VehicleBreakRules(BlockNames.resolveList(breakable), BlockNames.resolveList(unbreakable),
                    Stream.of(materials.split("\\s*,\\s*")).flatMap(name -> BlockMaterial.fromName(name).stream())
                            .toList());
        }

        public boolean isBreakableMaterial(BlockState state) {
            for (BlockMaterial material : this.materials) {
                if (material.matches(state)) {
                    return true;
                }
            }
            return false;
        }
    }
}
