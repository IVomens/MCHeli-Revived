package com.ivomens.mcheli.core.hud;

import com.ivomens.mcheli.core.hud.expr.ExprType;
import com.ivomens.mcheli.core.hud.expr.Variables;
import com.ivomens.mcheli.core.math.Vec2d;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The inputs of one HUD frame, read from the vehicle once per frame: the value of every {@link HudVariable},
 * typed, in the slots formulas were bound to when they compiled; the {@link HudTextValue}s {@code DrawString}
 * shows; and what the instrument commands draw (the selected weapon's magazine, the gunner camera, the radar
 * contacts). One frame object is filled again for every frame drawn, and formulas read it without boxing.
 */
public final class HudFrame implements Variables {

    private static final int VARIABLES = HudVariable.values().length;

    private final long[] ints = new long[VARIABLES];
    private final double[] doubles = new double[VARIABLES];
    private final boolean[] bools = new boolean[VARIABLES];
    private final Object[] texts = new Object[HudTextValue.values().length];
    private boolean hasWeapon;
    private int magazineSize;
    private boolean hasCamera;
    private float vehicleYaw;
    private float cameraYaw;
    private float cameraPitch;
    private List<Vec2d> radarContacts = List.of();
    private List<Vec2d> radarEnemies = List.of();

    public void set(HudVariable variable, long value) {
        this.ints[slot(variable, ExprType.INT)] = value;
    }

    public void set(HudVariable variable, double value) {
        this.doubles[slot(variable, ExprType.DOUBLE)] = value;
    }

    public void set(HudVariable variable, boolean value) {
        this.bools[slot(variable, ExprType.BOOL)] = value;
    }

    public long intValue(HudVariable variable) {
        return this.ints[slot(variable, ExprType.INT)];
    }

    public double doubleValue(HudVariable variable) {
        return this.doubles[slot(variable, ExprType.DOUBLE)];
    }

    public boolean boolValue(HudVariable variable) {
        return this.bools[slot(variable, ExprType.BOOL)];
    }

    /** Sets a text value; {@code text} must have the Java type {@code value} declares. */
    public void setText(HudTextValue value, @Nullable Object text) {
        if (!value.accepts(text)) {
            throw new IllegalArgumentException(value + " cannot show a " + text.getClass().getSimpleName());
        }
        this.texts[value.ordinal()] = text;
    }

    @Nullable
    public Object text(HudTextValue value) {
        return this.texts[value.ordinal()];
    }

    /** @param magazineSize the selected weapon's magazine; ammo texts are hidden when it is not positive */
    public void setWeapon(boolean hasWeapon, int magazineSize) {
        this.hasWeapon = hasWeapon;
        this.magazineSize = magazineSize;
    }

    /** Whether a weapon is selected, which the weapon texts need. */
    public boolean hasWeapon() {
        return this.hasWeapon;
    }

    public int magazineSize() {
        return this.magazineSize;
    }

    /** The gunner camera, in degrees. */
    public void setCamera(float vehicleYaw, float cameraYaw, float cameraPitch) {
        this.hasCamera = true;
        this.vehicleYaw = vehicleYaw;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
    }

    /** For a vehicle without a gunner camera, whose camera indicator draws nothing. */
    public void clearCamera() {
        this.hasCamera = false;
    }

    public boolean hasCamera() {
        return this.hasCamera;
    }

    public float vehicleYaw() {
        return this.vehicleYaw;
    }

    public float cameraYaw() {
        return this.cameraYaw;
    }

    public float cameraPitch() {
        return this.cameraPitch;
    }

    /** Radar contacts relative to the vehicle: every entity, and the enemies among them. */
    public void setRadar(List<Vec2d> contacts, List<Vec2d> enemies) {
        this.radarContacts = contacts;
        this.radarEnemies = enemies;
    }

    /** Every contact, or only the enemies. */
    public List<Vec2d> radarContacts(boolean allContacts) {
        return allContacts ? this.radarContacts : this.radarEnemies;
    }

    @Override
    public long intValue(int slot) {
        return this.ints[slot];
    }

    @Override
    public double doubleValue(int slot) {
        return this.doubles[slot];
    }

    @Override
    public boolean boolValue(int slot) {
        return this.bools[slot];
    }

    private static int slot(HudVariable variable, ExprType type) {
        if (variable.type() != type) {
            throw new IllegalArgumentException(variable.scriptName() + " is " + variable.type().description()
                    + ", not " + type.description());
        }
        return variable.ordinal();
    }
}
