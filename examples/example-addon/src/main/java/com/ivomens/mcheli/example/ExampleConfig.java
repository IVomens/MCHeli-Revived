package com.ivomens.mcheli.example;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The addon's server config: {@code serverconfig/mcheli_example-server.toml} in each world. */
public final class ExampleConfig {

    public static final ModConfigSpec SPEC;
    /** Whether shots by players inside the server's spawn protection are stopped. */
    public static final ModConfigSpec.BooleanValue BLOCK_FIRE_IN_SPAWN_PROTECTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        BLOCK_FIRE_IN_SPAWN_PROTECTION = builder
                .comment("Stop MCHeli weapons fired by players inside the server's spawn protection.")
                .define("blockFireInSpawnProtection", true);
        SPEC = builder.build();
    }

    private ExampleConfig() {
    }
}
