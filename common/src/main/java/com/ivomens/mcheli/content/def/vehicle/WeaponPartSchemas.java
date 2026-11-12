package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.Weapon;
import com.ivomens.mcheli.content.def.vehicle.Parts.WeaponChild;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.content.schema.Vectors;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.FLOAT;

/**
 * {@code Components.Weapon}: gun and launcher models that aim with their weapons.
 * <pre>
 * Weapon:
 * - Position: [0.0, -1.0, 2.0]
 *   Rotation: [0.0, 0.0, 0.0]
 *   PartName: weapon1
 *   WeaponNames: hydra70/m230    # the weapon types that move it
 *   Yaw: true
 *   Children:                    # barrels that move with it, named weapon1_0, weapon1_1, ...
 *   - Position: [0.0, -1.2, 2.2]
 *     Pitch: true
 * </pre>
 */
final class WeaponPartSchemas {

    static final Field<WeaponChild, Vec3> CHILD_POSITION = Field.of("Position", Vectors.VEC3, Vec3.ZERO,
            WeaponChild::position).txt(Vectors.TXT_FLOATS).alwaysWritten();
    static final Field<WeaponChild, Vec3> CHILD_ROTATION = Field.of("Rotation", Vectors.VEC3, Vec3.ZERO,
            WeaponChild::rotation).alwaysWritten();
    static final Field<WeaponChild, Boolean> CHILD_YAW = Field.of("Yaw", BOOLEAN, false, WeaponChild::yaw);
    static final Field<WeaponChild, Boolean> CHILD_PITCH = Field.of("Pitch", BOOLEAN, false, WeaponChild::pitch);
    static final Field<WeaponChild, Float> CHILD_RECOIL = Field.of("RecoilBuf", FLOAT, 0.0F,
            WeaponChild::recoilBuffer);
    static final EntrySchema<WeaponChild> CHILD = new EntrySchema<>("barrel",
            Section.root(CHILD_POSITION, CHILD_ROTATION, CHILD_YAW, CHILD_PITCH, CHILD_RECOIL),
            v -> new WeaponChild(v.get(CHILD_POSITION), v.get(CHILD_ROTATION), v.get(CHILD_YAW), v.get(CHILD_PITCH),
                    v.get(CHILD_RECOIL)));

    static final Placement<Weapon> PLACE = Placement.of("weapon", Weapon::position, Weapon::rotation,
            Weapon::name);
    static final Field<Weapon, List<String>> WEAPONS = Field.of("WeaponNames", VehicleCodecs.SLASHED_NAMES,
            List.of("unnamed"), Weapon::weapons).alwaysWritten();
    /** The barrels spin while firing. */
    static final Field<Weapon, Boolean> BARREL_ROTATION = Field.of("BarrelRot", BOOLEAN, false,
            Weapon::barrelRotation);
    static final Field<Weapon, Boolean> MISSILE = Field.of("IsMissile", BOOLEAN, false, Weapon::missile);
    /** Hidden in gunner view. */
    static final Field<Weapon, Boolean> HIDE_IN_GUNNER = Field.of("HideGM", BOOLEAN, false, Weapon::hideInGunner);
    static final Field<Weapon, Boolean> YAW = Field.of("Yaw", BOOLEAN, false, Weapon::yaw);
    static final Field<Weapon, Boolean> PITCH = Field.of("Pitch", BOOLEAN, false, Weapon::pitch);
    static final Field<Weapon, Float> RECOIL = Field.of("RecoilBuf", FLOAT, 0.0F, Weapon::recoilBuffer);
    static final Field<Weapon, Boolean> TURRET = Field.of("Turret", BOOLEAN, false, Weapon::turret);
    /** TXT: {@code AddPartWeaponChild} lines, which add to the weapon part before them. */
    static final Field<Weapon, List<WeaponChild>> CHILDREN = Field.listOf("Children", () -> CHILD,
            Weapon::children);
    static final EntrySchema<Weapon> WEAPON = PLACE.entry("weapon part",
            v -> new Weapon(PLACE.position(v), PLACE.rotation(v), PLACE.name(v), v.get(WEAPONS),
                    v.get(BARREL_ROTATION), v.get(MISSILE), v.get(HIDE_IN_GUNNER), v.get(YAW), v.get(PITCH),
                    v.get(RECOIL), v.get(TURRET), v.get(CHILDREN)),
            WEAPONS, BARREL_ROTATION, MISSILE, HIDE_IN_GUNNER, YAW, PITCH, RECOIL, TURRET, CHILDREN)
            .unsupported("RotBarrel");

    private WeaponPartSchemas() {
    }
}
