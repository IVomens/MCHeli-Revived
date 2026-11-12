package com.ivomens.mcheli.fx;

import com.ivomens.mcheli.fx.client.legacy.LegacyEffects;
import net.minecraft.world.level.Level;

/**
 * Parameters of a smoke or splash particle for {@link Effects#spawn(ParticleParams)}, set up by the effect that
 * asks for it and read once by the effect style ({@link LegacyEffects} or the modern one) that spawns it. An
 * effect may change and spawn the same parameters again for each particle of a burst.
 */
public final class ParticleParams {

    private final Level world;
    private final String name;
    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private float size = 1.0F;
    private float alpha = 1.0F;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private boolean windBlown;
    private int age;
    private boolean diffusible;
    private boolean turnsWhite;
    private float gravity;
    private float risesAfter = 2.0F;

    /** A particle named {@code name} ({@code smoke}, {@code splash} or {@code explode}) at (x, y, z). */
    public ParticleParams(Level level, String name, double x, double y, double z) {
        this.world = level;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ParticleParams(Level level, String name, double x, double y, double z, double mx, double my, double mz,
                          float size) {
        this(level, name, x, y, z);
        this.motionX = mx;
        this.motionY = my;
        this.motionZ = mz;
        this.size = size;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setMotion(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }

    public void setSize(float size) {
        this.size = size;
    }

    public void setColor(float alpha, float red, float green, float blue) {
        this.alpha = alpha;
        this.setRgb(red, green, blue);
    }

    public void setRgb(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    /** Ticks the particle lives; 0 keeps the particle's own lifetime. */
    public void setAge(int age) {
        this.age = age;
    }

    /** The particle drifts with the wind. */
    public void setWindBlown(boolean windBlown) {
        this.windBlown = windBlown;
    }

    /** The particle starts small and grows. */
    public void setDiffusible(boolean diffusible) {
        this.diffusible = diffusible;
    }

    /** The particle fades to white as it ages. */
    public void setTurnsWhite(boolean turnsWhite) {
        this.turnsWhite = turnsWhite;
    }

    /** Extra vertical acceleration per tick. */
    public void setGravity(float gravity) {
        this.gravity = gravity;
    }

    /** The fraction of its lifetime after which the particle starts to rise. */
    public void setRisesAfter(float fractionOfLifetime) {
        this.risesAfter = fractionOfLifetime;
    }

    public Level world() {
        return this.world;
    }

    public String name() {
        return this.name;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public double motionX() {
        return this.motionX;
    }

    public double motionY() {
        return this.motionY;
    }

    public double motionZ() {
        return this.motionZ;
    }

    public float size() {
        return this.size;
    }

    public float alpha() {
        return this.alpha;
    }

    public float red() {
        return this.red;
    }

    public float green() {
        return this.green;
    }

    public float blue() {
        return this.blue;
    }

    public boolean windBlown() {
        return this.windBlown;
    }

    public int age() {
        return this.age;
    }

    public boolean diffusible() {
        return this.diffusible;
    }

    public boolean turnsWhite() {
        return this.turnsWhite;
    }

    public float gravity() {
        return this.gravity;
    }

    public float risesAfter() {
        return this.risesAfter;
    }
}
