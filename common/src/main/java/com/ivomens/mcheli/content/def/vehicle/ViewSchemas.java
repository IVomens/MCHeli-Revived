package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.SoundCodec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import com.ivomens.mcheli.content.def.vehicle.CrewEntries.Splash;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Armor;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.CameraSettings;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Render;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Sound;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Uav;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;
import static com.ivomens.mcheli.core.content.schema.Codecs.INT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * The YAML sections about how the vehicle is seen and heard, and how tough it is: {@code Camera},
 * {@code Sound}, {@code Render}, {@code Armor} and {@code Uav}.
 */
final class ViewSchemas {

    static final Field<CameraSettings, Float> THIRD_PERSON_DISTANCE = Field.of("ThirdPersonDist",
            floats(4, 100), 4.0F, CameraSettings::thirdPersonDistance).txt("thirdpersondist");
    /** TXT: the fourth value of a {@code CameraPosition} line. */
    static final Field<CameraSettings, Boolean> ALWAYS_CAMERA_VIEW =
            Field.of("AlwaysCameraView", BOOLEAN, false, CameraSettings::alwaysCameraView);
    static final Field<CameraSettings, Boolean> DEFAULT_FREE_LOOK =
            Field.of("DefaultFreeLook", BOOLEAN, false, CameraSettings::defaultFreeLook).txt("defaultfreelook");
    static final Field<CameraSettings, Float> ROTATION_SPEED = Field.of("RotationSpeed", floats(0, 10_000),
            100.0F, CameraSettings::rotationSpeed).txt("camerarotationspeed");
    /** TXT: {@code CameraPosition} lines. */
    static final Field<CameraSettings, List<CameraSpec>> POSITIONS =
            Field.listOf("Pos", () -> CrewSchema.CAMERA, CameraSettings::positions).alias("Positons");

    static final Field<Sound, ResourceLocation> MOVE_SOUND =
            Field.of("MoveSound", SoundCodec.SOUND, (ResourceLocation) null, Sound::moveSound).txt("sound");
    static final Field<Sound, Float> VOLUME = Field.of("Vol", floats(0, 10), 1.0F, Sound::volume).alias("Volume")
            .txt("soundvolume");
    static final Field<Sound, Float> PITCH = Field.of("Pitch", floats(0, 10), 1.0F, Sound::pitch).txt("soundpitch");

    /** TXT: {@code AddTexture} lines, in lower case. */
    static final Field<Render, List<String>> TEXTURES = Field.of("Textures", VehicleCodecs.NAMES, List.of(),
            Render::textures);
    static final Field<Render, Boolean> SMOOTH_SHADING =
            Field.of("SmoothShading", BOOLEAN, true, Render::smoothShading).txt("smoothshading");
    static final Field<Render, Boolean> HIDE_RIDERS =
            Field.of("HideRiders", BOOLEAN, false, Render::hideRiders).txt("hideentity");
    static final Field<Render, Float> MODEL_WIDTH = Field.of("ModelWidth", FLOAT, 1.0F, Render::modelWidth)
            .txt(floats(-100, 100), "entitywidth");
    static final Field<Render, Float> MODEL_HEIGHT = Field.of("ModelHeight", FLOAT, 1.0F, Render::modelHeight)
            .txt(floats(-100, 100), "entityheight");
    static final Field<Render, Float> MODEL_PITCH = Field.of("ModelPitch", FLOAT, 0.0F, Render::modelPitch)
            .txt(floats(-360, 360), "entitypitch");
    static final Field<Render, Float> MODEL_ROLL = Field.of("ModelRoll", FLOAT, 0.0F, Render::modelRoll)
            .txt(floats(-360, 360), "entityroll");
    static final Field<Render, Float> PARTICLE_SCALE =
            Field.of("ParticleScale", floats(0, 50), 1.0F, Render::particleScale).txt("particlesscale");
    static final Field<Render, Boolean> SEA_SURFACE_PARTICLES = Field.of("EnableSeaSurfaceParticle", BOOLEAN,
            false, Render::seaSurfaceParticles).txt("enableseasurfaceparticle");
    /** TXT: {@code AddParticleSplash} lines. */
    static final Field<Render, List<Splash>> SPLASHES =
            Field.listOf("SplashParticles", () -> ViewSchemas.SPLASH, Render::splashParticles);

    static final Field<Splash, Vec3> SPLASH_POSITION = Field.of("Position", Vectors.VEC3, null, Splash::position)
            .alias("Pos");
    static final Field<Splash, Integer> SPLASH_COUNT = Field.of("Count", ints(1, 100), 2, Splash::count);
    static final Field<Splash, Float> SPLASH_SIZE = Field.of("Size", FLOAT, 2.0F, Splash::size);
    static final Field<Splash, Float> SPLASH_ACCELERATION =
            Field.of("Acceleration", FLOAT, 1.0F, Splash::acceleration).alias("Accel");
    static final Field<Splash, Integer> SPLASH_AGE = Field.of("Age", ints(1, 100_000), 80, Splash::age);
    static final Field<Splash, Float> SPLASH_MOTION = Field.of("Motion", FLOAT, 0.01F, Splash::motion);
    static final Field<Splash, Float> SPLASH_GRAVITY = Field.of("Gravity", FLOAT, 0.0F, Splash::gravity);
    static final EntrySchema<Splash> SPLASH = new EntrySchema<>("splash particle",
            Section.root(SPLASH_POSITION, SPLASH_COUNT, SPLASH_SIZE, SPLASH_ACCELERATION, SPLASH_AGE,
                    SPLASH_MOTION, SPLASH_GRAVITY).requires(SPLASH_POSITION),
            v -> new Splash(v.get(SPLASH_POSITION), v.get(SPLASH_COUNT), v.get(SPLASH_SIZE),
                    v.get(SPLASH_ACCELERATION), v.get(SPLASH_AGE), v.get(SPLASH_MOTION), v.get(SPLASH_GRAVITY)));

    static final Section<VehicleFile, Render> RENDER = Section.of("Render", VehicleFile::render, TEXTURES,
            SMOOTH_SHADING, HIDE_RIDERS, MODEL_WIDTH, MODEL_HEIGHT, MODEL_PITCH, MODEL_ROLL, PARTICLE_SCALE,
            SEA_SURFACE_PARTICLES, SPLASHES).unsupported("OneProbeScale", "SignMarkers");

    static final Field<Armor, Float> ARMOR_DAMAGE_FACTOR = Field.of("ArmorDamageFactor", floats(0, 10_000), 1.0F,
            Armor::armorDamageFactor).txt("armordamagefactor");
    static final Field<Armor, Float> ARMOR_MIN_DAMAGE = Field.of("ArmorMinDamage", floats(0, 1_000_000), 0.0F,
            Armor::armorMinDamage).txt("armormindamage");
    static final Field<Armor, Float> ARMOR_MAX_DAMAGE = Field.of("ArmorMaxDamage", floats(0, 1_000_000),
            100_000.0F, Armor::armorMaxDamage).txt("armormaxdamage");
    static final Field<Armor, Float> DAMAGE_FACTOR =
            Field.of("DamageFactor", floats(0, 1), 0.2F, Armor::damageFactor).txt("damagefactor");
    static final Field<Armor, Float> SUBMERGED_DAMAGE_HEIGHT = Field.of("SubmergedDamageHeight",
            floats(-1000, 1000), 0.0F, Armor::submergedDamageHeight).txt("submergeddamageheight");
    static final Section<VehicleFile, Armor> ARMOR = Section.of("Armor", VehicleFile::armor, ARMOR_DAMAGE_FACTOR,
            ARMOR_MIN_DAMAGE, ARMOR_MAX_DAMAGE, DAMAGE_FACTOR, SUBMERGED_DAMAGE_HEIGHT);

    /** TXT: {@code UAV} and {@code SmallUAV} lines. */
    static final Field<Uav, Boolean> UAV = Field.of("IsUav", BOOLEAN, false, Uav::uav);
    static final Field<Uav, Boolean> SMALL_UAV = Field.of("IsSmallUav", BOOLEAN, false, Uav::small);
    static final Field<Uav, Boolean> NEW_UAV = Field.of("IsNewUav", BOOLEAN, false, Uav::newStyle);
    static final Field<Uav, Boolean> TARGET_DRONE = Field.of("IsTargetDrone", BOOLEAN, false, Uav::targetDrone)
            .txt("targetdrone");
    /** Blocks; -1 for the wingman controller's range. */
    static final Field<Uav, Integer> UAV_RANGE = Field.of("Range", INT, -1, Uav::range).alias("ControlRange");
    static final Section<VehicleFile, Uav> UAV_SECTION = Section.of("Uav", VehicleFile::uav, UAV, SMALL_UAV,
            NEW_UAV, TARGET_DRONE, UAV_RANGE);

    private ViewSchemas() {
    }

    static Section<VehicleFile, CameraSettings> camera(KindFields kind) {
        return Section.of("Camera", VehicleFile::camera, THIRD_PERSON_DISTANCE, ALWAYS_CAMERA_VIEW, kind.zoom(),
                DEFAULT_FREE_LOOK, ROTATION_SPEED, POSITIONS);
    }

    static Section<VehicleFile, Sound> sound(KindFields kind) {
        return Section.of("Sound", VehicleFile::sound, MOVE_SOUND, VOLUME, PITCH, kind.soundRange());
    }

    static CameraSettings camera(Values v, KindFields kind) {
        return new CameraSettings(v.get(THIRD_PERSON_DISTANCE), v.get(ALWAYS_CAMERA_VIEW), v.get(kind.zoom()),
                v.get(DEFAULT_FREE_LOOK), v.get(ROTATION_SPEED), v.get(POSITIONS));
    }

    static Sound sound(Values v, KindFields kind) {
        return new Sound(v.get(MOVE_SOUND), v.get(VOLUME), v.get(PITCH), v.get(kind.soundRange()));
    }

    static Render render(Values v) {
        return new Render(v.get(TEXTURES), v.get(SMOOTH_SHADING), v.get(HIDE_RIDERS), v.get(MODEL_WIDTH),
                v.get(MODEL_HEIGHT), v.get(MODEL_PITCH), v.get(MODEL_ROLL), v.get(PARTICLE_SCALE),
                v.get(SEA_SURFACE_PARTICLES), v.get(SPLASHES));
    }

    static Armor armor(Values v) {
        return new Armor(v.get(ARMOR_DAMAGE_FACTOR), v.get(ARMOR_MIN_DAMAGE), v.get(ARMOR_MAX_DAMAGE),
                v.get(DAMAGE_FACTOR), v.get(SUBMERGED_DAMAGE_HEIGHT));
    }

    static Uav uav(Values v) {
        return new Uav(v.get(UAV), v.get(SMALL_UAV), v.get(NEW_UAV), v.get(TARGET_DRONE), v.get(UAV_RANGE));
    }
}
