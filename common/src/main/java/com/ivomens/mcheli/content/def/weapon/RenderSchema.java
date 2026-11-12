package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Render;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition.Smoke;
import com.ivomens.mcheli.core.content.schema.ArgbColor;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.LOWER_TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * YAML {@code Render}: how the shot looks. Legacy TXT adds flashes, casings and colours with multi-value lines;
 * see {@link WeaponTxtRules}.
 */
final class RenderSchema {

    /** A particle name; {@code none} turns the trail off. */
    static final ValueCodec<String> PARTICLE = LOWER_TEXT.map(
            name -> name.equals("none") ? "" : name,
            name -> name.isEmpty() ? "none" : name);

    static final Field<Render, List<MuzzleFlashSpec>> MUZZLE_FLASHES =
            Field.of("MuzzleFlashes", MuzzleFlashSpec.LIST_CODEC, List.of(), Render::muzzleFlashes);
    static final Field<Render, List<MuzzleFlashSpec>> MUZZLE_SMOKE =
            Field.of("MuzzleSmoke", MuzzleFlashSpec.LIST_CODEC, List.of(), Render::muzzleSmoke);
    static final Field<Render, CasingSpec> SPENT_CASINGS =
            Field.of("SpentCasings", CasingSpec.CODEC, (CasingSpec) null, Render::spentCasings);
    /** Bullet model name; empty for the weapon type's default model. */
    static final Field<Render, String> BULLET_MODEL =
            Field.of("BulletModel", LOWER_TEXT, "", Render::bulletModel).txt("modelbullet");
    static final Field<Render, String> BOMBLET_MODEL =
            Field.of("BombletModel", LOWER_TEXT, "", Render::bombletModel).txt("modelbomblet");
    static final Field<Render, String> TRAJECTORY_PARTICLE =
            Field.of("TrajectoryParticle", PARTICLE, "explode", Render::trajectoryParticle).txt("trajectoryparticle");
    static final Field<Render, Integer> TRAJECTORY_PARTICLE_START_TICK =
            Field.of("TrajectoryParticleStartTick", ints(0, 10_000), 0, Render::trajectoryParticleStartTick)
                    .txt("trajectoryparticlestarttick");
    /** Block-break particles on impact. */
    static final Field<Render, Integer> FLAK_PARTICLES_CRACK =
            Field.of("FlakParticlesCrack", ints(0, 300), 10, Render::flakParticlesCrack).txt("flakparticlescrack");
    /** White smoke particles on impact. */
    static final Field<Render, Integer> PARTICLES_FLAK =
            Field.of("ParticlesFlak", ints(0, 100), 3, Render::particlesFlak).txt("particlesflak");

    static final Field<Smoke, Boolean> SMOKE_DISABLED =
            Field.of("DisableSmoke", BOOLEAN, false, Smoke::disabled).txt("disablesmoke");
    static final Field<Smoke, Float> SMOKE_SIZE =
            Field.of("SmokeSize", floats(0, 100), 2.0F, Smoke::size).txt("smokesize");
    static final Field<Smoke, Integer> SMOKE_COUNT = Field.of("SmokeNum", ints(1, 100), 1, Smoke::count)
            .txt("smokenum");
    /** Ticks a smoke particle lives. */
    static final Field<Smoke, Integer> SMOKE_MAX_AGE =
            Field.of("SmokeMaxAge", ints(2, 1000), 100, Smoke::maxAge).txt("smokemaxage");
    static final Field<Smoke, ArgbColor> BULLET_COLOR_IN_WATER =
            Field.of("BulletColorInWater", ArgbColor.CODEC, ArgbColor.WHITE, Smoke::bulletColorInWater)
                    .alwaysWritten();
    /** Also tints the smoke trail. */
    static final Field<Smoke, ArgbColor> BULLET_COLOR =
            Field.of("BulletColor", ArgbColor.CODEC, ArgbColor.WHITE, Smoke::bulletColor).alwaysWritten();
    static final Section<Render, Smoke> SMOKE = Section.of("Smoke", Render::smoke, SMOKE_DISABLED, SMOKE_SIZE,
            SMOKE_COUNT, SMOKE_MAX_AGE, BULLET_COLOR_IN_WATER, BULLET_COLOR);

    static final Section<WeaponDefinition, Render> SECTION = Section.of("Render", WeaponDefinition::render,
                    MUZZLE_FLASHES, MUZZLE_SMOKE, SPENT_CASINGS, BULLET_MODEL, BOMBLET_MODEL, TRAJECTORY_PARTICLE,
                    TRAJECTORY_PARTICLE_START_TICK, FLAK_PARTICLES_CRACK, PARTICLES_FLAK, SMOKE)
            .unsupported("TrajectoryParticleEndTick", "EnableExhaustFlare", "BulletModelEnd");

    private RenderSchema() {
    }

    static Render build(Values v) {
        return new Render(v.get(MUZZLE_FLASHES), v.get(MUZZLE_SMOKE), v.get(SPENT_CASINGS), v.get(BULLET_MODEL),
                v.get(BOMBLET_MODEL), v.get(TRAJECTORY_PARTICLE), v.get(TRAJECTORY_PARTICLE_START_TICK),
                v.get(FLAK_PARTICLES_CRACK), v.get(PARTICLES_FLAK),
                new Smoke(v.get(SMOKE_DISABLED), v.get(SMOKE_SIZE), v.get(SMOKE_COUNT), v.get(SMOKE_MAX_AGE),
                        v.get(BULLET_COLOR_IN_WATER), v.get(BULLET_COLOR)));
    }

    /** A legacy TXT colour, {@code a, r, g, b} from 0 to 255 (alpha first); see {@link WeaponTxtRules#color}. */
    static ArgbColor txtColor(String[] channels, ReadContext context) {
        return new ArgbColor(ArgbColor.txtChannel(channels[0], context), ArgbColor.txtChannel(channels[1], context),
                ArgbColor.txtChannel(channels[2], context), ArgbColor.txtChannel(channels[3], context));
    }
}
