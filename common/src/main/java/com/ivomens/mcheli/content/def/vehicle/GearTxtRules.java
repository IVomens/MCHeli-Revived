package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.Parts.LandingGear;
import com.ivomens.mcheli.content.def.vehicle.Parts.Weapon;
import com.ivomens.mcheli.content.def.vehicle.Parts.WeaponChild;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.Schema;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import net.minecraft.world.phys.Vec3;

import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.LANDING_GEAR;
import static com.ivomens.mcheli.content.def.vehicle.ComponentsSchema.WEAPONS;

/** Legacy TXT lines that add landing gear and weapon parts; see {@link PartTxtRules}. */
final class GearTxtRules {

    /** Values of a gear line with a second axis; fewer than all three fail the file, as they always did. */
    private static final int SECOND_AXIS_GIVEN = 8;
    private static final int SECOND_AXIS_COMPLETE = 10;
    /** A turning weapon part without its axis turns about -z. */
    private static final Vec3 BARREL_AXIS = new Vec3(0.0, 0.0, -1.0);

    /**
     * {@code AddPartLG = x, y, z, rx, ry, rz[, opening[, rx2, ry2, rz2[, opening2]]]}; {@code AddPartLGRev}
     * folds the other way, {@code AddPartLGHatch} is a gear door.
     */
    static final TxtTuple<LandingGear> GEAR = gear(false, false);
    static final TxtTuple<LandingGear> REVERSE_GEAR = gear(true, false);
    static final TxtTuple<LandingGear> GEAR_HATCH = gear(false, true);
    /** {@code AddPartSlideRotLG = slideX, slideY, slideZ, x, y, z, rx, ry, rz[, opening]}. */
    static final TxtTuple<LandingGear> SLIDING_GEAR = TxtTuple.of(PartSchemas.LANDING_GEAR).minimum(9)
            .vector(PartSchemas.GEAR_SLIDE)
            .vector(PartSchemas.GEAR_PLACE.position()).vector(PartSchemas.GEAR_PLACE.rotation())
            .value(PartSchemas.GEAR_OPENING)
            .then(PartTxtRules.named(PartSchemas.GEAR_PLACE.name(), "lg", LANDING_GEAR));

    /**
     * {@code AddPartWeapon = weapons, hideInGunner, yaw, pitch, x, y, z[, recoil]}, weapons separated by
     * {@code /}. {@code AddPartTurretWeapon} turns with the turret, {@code AddPartWeaponMissile} is a missile.
     */
    static final TxtTuple<Weapon> WEAPON = weapon(false, false, false);
    static final TxtTuple<Weapon> TURRET_WEAPON = weapon(false, false, true);
    static final TxtTuple<Weapon> MISSILE = weapon(false, true, false);
    /**
     * {@code AddPartRotWeapon = weapons, hideInGunner, yaw, pitch, x, y, z[, rx, ry, rz]}: the barrels spin
     * about the given axis; {@code AddPartTurretRotWeapon} also turns with the turret.
     */
    static final TxtTuple<Weapon> ROTARY_WEAPON = weapon(true, false, false);
    static final TxtTuple<Weapon> TURRET_ROTARY_WEAPON = weapon(true, false, true);

    /** {@code AddPartWeaponChild = yaw, pitch, x, y, z[, recoil]}: a barrel of the weapon part before it. */
    static final TxtTuple<WeaponChild> WEAPON_CHILD = TxtTuple.of(WeaponPartSchemas.CHILD).minimum(5)
            .value(WeaponPartSchemas.CHILD_YAW).value(WeaponPartSchemas.CHILD_PITCH)
            .vector(WeaponPartSchemas.CHILD_POSITION)
            .value(WeaponPartSchemas.CHILD_RECOIL);

    private GearTxtRules() {
    }

    static void register(Schema.Builder<VehicleFile> builder) {
        builder.txtRule("addpartlg", GEAR.appendingTo(LANDING_GEAR))
                .txtRule("addpartlgrev", REVERSE_GEAR.appendingTo(LANDING_GEAR))
                .txtRule("addpartlghatch", GEAR_HATCH.appendingTo(LANDING_GEAR))
                .txtRule("addpartsliderotlg", SLIDING_GEAR.appendingTo(LANDING_GEAR))
                .txtRule("addpartweapon", WEAPON.appendingTo(WEAPONS))
                .txtRule("addpartturretweapon", TURRET_WEAPON.appendingTo(WEAPONS))
                .txtRule("addpartweaponmissile", MISSILE.appendingTo(WEAPONS))
                .txtRule("addpartrotweapon", ROTARY_WEAPON.appendingTo(WEAPONS))
                .txtRule("addpartturretrotweapon", TURRET_ROTARY_WEAPON.appendingTo(WEAPONS))
                .txtRule("addpartweaponchild", (values, text, context) -> {
                    int index = values.get(WEAPONS).isEmpty() ? 0 : values.get(WEAPONS).getLast().children().size();
                    WEAPON_CHILD.read(text, values, index, context).ifPresent(child ->
                            values.updateLast(WEAPONS, weapon -> weapon.withChild(child)));
                });
    }

    private static TxtTuple<LandingGear> gear(boolean reverse, boolean hatch) {
        return TxtTuple.of(PartSchemas.LANDING_GEAR).minimum(6)
                .fixed(PartSchemas.GEAR_REVERSE, reverse).fixed(PartSchemas.GEAR_HATCH, hatch)
                .vector(PartSchemas.GEAR_PLACE.position()).vector(PartSchemas.GEAR_PLACE.rotation())
                .value(PartSchemas.GEAR_OPENING)
                .vector(PartSchemas.GEAR_SECOND_AXIS)
                .value(PartSchemas.GEAR_SECOND_OPENING)
                .then((part, file, count) -> {
                    if (count >= SECOND_AXIS_GIVEN && count < SECOND_AXIS_COMPLETE) {
                        throw new ContentValueException("not enough values");
                    }
                    PartTxtRules.named(PartSchemas.GEAR_PLACE.name(), "lg", LANDING_GEAR).apply(part, file, count);
                });
    }

    private static TxtTuple<Weapon> weapon(boolean rotary, boolean missile, boolean turret) {
        TxtTuple<Weapon> tuple = TxtTuple.of(WeaponPartSchemas.WEAPON).minimum(7)
                .fixed(WeaponPartSchemas.BARREL_ROTATION, rotary)
                .fixed(WeaponPartSchemas.MISSILE, missile)
                .fixed(WeaponPartSchemas.TURRET, turret)
                .value(WeaponPartSchemas.WEAPONS)
                .value(WeaponPartSchemas.HIDE_IN_GUNNER)
                .value(WeaponPartSchemas.YAW).value(WeaponPartSchemas.PITCH)
                .vector(WeaponPartSchemas.PLACE.position());
        tuple = rotary ? tuple.vector(WeaponPartSchemas.PLACE.rotation(), BARREL_AXIS)
                : tuple.value(WeaponPartSchemas.RECOIL);
        return tuple.then(PartTxtRules.named(WeaponPartSchemas.PLACE.name(), "weapon", WEAPONS));
    }
}
