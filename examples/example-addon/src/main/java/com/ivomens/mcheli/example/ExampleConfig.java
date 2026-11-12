package com.ivomens.mcheli.example;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The addon's server config: {@code serverconfig/mcheli_example-server.toml} in each world. */
public final class ExampleConfig {

    public static final ModConfigSpec SPEC;
    /** Whether shots by players inside the server's spawn protection are stopped. */
    public static final ModConfigSpec.BooleanValue BLOCK_FIRE_IN_SPAWN_PROTECTION;
    /** Whether players inside the server's spawn protection may not fly or place aircraft. */
    public static final ModConfigSpec.BooleanValue NO_FLY_ZONE_AT_SPAWN;
    /** The namespace of a mod whose damage types vehicles take half damage from; empty for none. */
    public static final ModConfigSpec.ConfigValue<String> HALF_DAMAGE_FROM_MOD;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BLOCK_FIRE_IN_SPAWN_PROTECTION = builder
                .comment("Stop MCHeli weapons fired by players inside the server's spawn protection.")
                .define("blockFireInSpawnProtection", true);
        NO_FLY_ZONE_AT_SPAWN = builder
                .comment("Players inside the server's spawn protection may not take the pilot's seat of a",
                        "helicopter or plane, or place one.")
                .define("noFlyZoneAtSpawn", true);
        HALF_DAMAGE_FROM_MOD = builder
                .comment("MCHeli vehicles take half damage from the damage types of this mod (its namespace,",
                        "such as \"othergunmod\"). Empty for none.")
                .define("halfDamageFromMod", "");
        SPEC = builder.build();
    }

    private ExampleConfig() {
    }
}
