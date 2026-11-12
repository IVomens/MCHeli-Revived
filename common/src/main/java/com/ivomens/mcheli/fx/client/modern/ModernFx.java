package com.ivomens.mcheli.fx.client.modern;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The modern particle effects. Each method builds one complete effect (an explosion, a smoke
 * trail puff, rotor dust) from {@link FxParticle}s.
 * <p>
 * Counts scale with the player's particle setting and with distance, so a battle on the other
 * side of the valley still reads as smoke and fire without filling the particle engine.
 */
public final class ModernFx {

    private static final RandomSource RANDOM = RandomSource.create();

    /** Beyond this distance effects are thinned out, beyond FAR_DISTANCE heavily so. */
    private static final double NEAR_DISTANCE = 48;
    private static final double FAR_DISTANCE = 160;

    private ModernFx() {
    }

    // Effects

    /**
     * Fireball, smoke column, debris, sparks, a flash and a ground shockwave.
     *
     * @param power explosion strength as used by the weapon definitions (TNT is 4)
     */
    public static void explosion(double x, double y, double z, float power) {
        ClientLevel level = level();
        if (level == null) return;
        float size = Mth.clamp(power, 0.5F, 12F);
        float budget = budget(x, y, z);

        add(level, x, y, z, FxParticle.of(FxSprite.FLASH).lifetime(5).size(size * 1.0F, size * 1.8F).fadeIn(0));

        fireball(level, x, y, z, size, budget);
        debris(level, x, y, z, size, budget);

        if (size >= 2) {
            add(level, x, y + 0.1, z, FxParticle.of(FxSprite.SHOCKWAVE).lifetime(12).size(size, size * 6).fadeIn(0));
        }
    }

    /** The flames and the smoke column of an explosion of the given (clamped) size. */
    private static void fireball(ClientLevel level, double x, double y, double z, float size, float budget) {
        int fire = count(6 + size * 3, budget);
        for (int i = 0; i < fire; i++) {
            double[] v = randomDirection(0.08 + 0.05 * size);
            add(level, x, y, z, FxParticle.of(FxSprite.FLAME)
                    .velocity(v[0], v[1] * 0.7 + 0.04, v[2])
                    .lifetime(14 + RANDOM.nextInt(10))
                    .size(size * 0.6F, size * 1.4F)
                    .spin(spin(0.05F))
                    .drag(0.88F));
        }

        int smoke = count(4 + size * 2F, budget);
        for (int i = 0; i < smoke; i++) {
            double[] v = randomDirection(0.05 + 0.03 * size);
            float grey = 0.22F + RANDOM.nextFloat() * 0.18F;
            add(level, x, y + 0.3 * size, z, FxParticle.of(FxSprite.SMOKE)
                    .velocity(v[0], Math.abs(v[1]) * 0.5 + 0.05, v[2])
                    .lifetime(70 + RANDOM.nextInt(50))
                    .size(size * 0.6F, size * 2.0F)
                    .color(grey, grey, grey)
                    .alpha(0.6F)
                    .fadeIn(0.3F)
                    .lift(0.004F)
                    .spin(spin(0.02F))
                    .drag(0.93F));
        }
    }

    /** The bouncing debris and the sparks thrown out by an explosion of the given (clamped) size. */
    private static void debris(ClientLevel level, double x, double y, double z, float size, float budget) {
        int debris = count(4 + size * 2, budget);
        for (int i = 0; i < debris; i++) {
            double[] v = randomDirection(0.25 + 0.06 * size);
            add(level, x, y, z, FxParticle.of(FxSprite.DEBRIS)
                    .velocity(v[0], Math.abs(v[1]) + 0.2, v[2])
                    .lifetime(40 + RANDOM.nextInt(30))
                    .size(0.12F + RANDOM.nextFloat() * 0.18F, 0.12F)
                    .gravity(1.2F)
                    .spin(spin(0.4F))
                    .drag(0.98F)
                    .fadeIn(0)
                    .randomFrame()
                    .bounces());
        }

        int sparks = count(8 + size * 3, budget);
        for (int i = 0; i < sparks; i++) {
            double[] v = randomDirection(0.35 + 0.05 * size);
            add(level, x, y, z, FxParticle.of(FxSprite.SPARK)
                    .velocity(v[0], v[1] + 0.1, v[2])
                    .lifetime(8 + RANDOM.nextInt(10))
                    .size(0.25F, 0.1F)
                    .gravity(0.6F)
                    .drag(0.9F)
                    .fadeIn(0));
        }
    }

    /** One puff of engine exhaust or missile trail. */
    public static void exhaust(double x, double y, double z, double vx, double vy, double vz, float scale, float grey) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                .velocity(vx, vy, vz)
                .lifetime(30 + RANDOM.nextInt(20))
                .size(0.3F * scale, 1.6F * scale)
                .color(grey, grey, grey)
                .alpha(0.6F)
                .lift(0.001F)
                .spin(spin(0.03F))
                .drag(0.92F));
    }

    /** Thick black smoke from a damaged or burning vehicle. */
    public static void damageSmoke(double x, double y, double z, float scale, boolean burning) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        float grey = 0.08F + RANDOM.nextFloat() * 0.08F;
        add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                .velocity(jitter(0.02), 0.04, jitter(0.02))
                .lifetime(60 + RANDOM.nextInt(40))
                .size(0.6F * scale, 2.8F * scale)
                .color(grey, grey, grey)
                .alpha(0.8F)
                .lift(0.002F)
                .spin(spin(0.02F))
                .drag(0.95F));
        if (burning && RANDOM.nextInt(3) == 0) {
            add(level, x, y, z, FxParticle.of(FxSprite.FLAME)
                    .velocity(jitter(0.02), 0.05, jitter(0.02))
                    .lifetime(10 + RANDOM.nextInt(8))
                    .size(0.5F * scale, 0.9F * scale)
                    .spin(spin(0.05F)));
        }
    }

    /**
     * Dust kicked up by rotor downwash or jet blast: a ring of low, fast-spreading puffs
     * tinted by the ground material.
     */
    public static void groundWash(double x, double y, double z, float radius, float r, float g, float b) {
        ClientLevel level = level();
        if (level == null) return;
        int puffs = count(radius * 1.5F, budget(x, y, z));
        for (int i = 0; i < puffs; i++) {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            double dx = Mth.cos(angle), dz = Mth.sin(angle);
            double start = radius * 0.3 * RANDOM.nextFloat();
            add(level, x + dx * start, y + 0.2, z + dz * start, FxParticle.of(FxSprite.DUST)
                    .velocity(dx * 0.35, 0.02, dz * 0.35)
                    .lifetime(20 + RANDOM.nextInt(15))
                    .size(0.8F, 2.4F)
                    .color(r, g, b)
                    .alpha(0.55F)
                    .spin(spin(0.04F))
                    .drag(0.9F));
        }
    }

    /** Dust thrown up from a block, tinted with the block's map colour. */
    public static void blockDust(double x, double y, double z, double vx, double vy, double vz, float scale, int rgb) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        float r = (rgb >> 16 & 0xFF) / 255F, g = (rgb >> 8 & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        add(level, x, y, z, FxParticle.of(FxSprite.DUST)
                .velocity(vx * 0.3, vy * 0.3, vz * 0.3)
                .lifetime(20 + RANDOM.nextInt(15))
                .size(0.15F * scale, 0.5F * scale)
                .color(0.55F + r * 0.45F, 0.55F + g * 0.45F, 0.55F + b * 0.45F)
                .alpha(0.5F)
                .spin(spin(0.04F))
                .drag(0.9F));
    }

    /** Spray and mist where something hits water. */
    public static void splash(double x, double y, double z, float size) {
        ClientLevel level = level();
        if (level == null) return;
        int drops = count(6 + size * 4, budget(x, y, z));
        for (int i = 0; i < drops; i++) {
            double[] v = randomDirection(0.1 + 0.04 * size);
            add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                    .velocity(v[0] * 0.6, Math.abs(v[1]) + 0.25 + 0.05 * size, v[2] * 0.6)
                    .lifetime(18 + RANDOM.nextInt(12))
                    .size(0.3F * size, 1.2F * size)
                    .color(0.85F, 0.92F, 1.0F)
                    .alpha(0.7F)
                    .gravity(1.0F)
                    .drag(0.95F));
        }
    }

    /** Muzzle flash with a short smoke puff. */
    public static void muzzleFlash(double x, double y, double z, float size) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        add(level, x, y, z, FxParticle.of(FxSprite.FLASH).lifetime(3).size(size, size * 1.3F).fadeIn(0));
        add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                .velocity(jitter(0.02), 0.02, jitter(0.02))
                .lifetime(18)
                .size(0.2F * size, 0.9F * size)
                .color(0.7F, 0.7F, 0.7F)
                .alpha(0.4F));
    }

    /** Burning countermeasure flare: a bright core that leaves a smoke trail. */
    public static void flareTrail(double x, double y, double z) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        add(level, x, y, z, FxParticle.of(FxSprite.FLASH).lifetime(4).size(0.9F, 0.6F).fadeIn(0));
        add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                .lifetime(40)
                .size(0.3F, 1.4F)
                .color(0.92F, 0.92F, 0.92F)
                .alpha(0.6F)
                .lift(0.0008F));
    }

    /**
     * Smoke defined by a weapon or vehicle definition (trails, exhaust, smoke weapons). Keeps the
     * definition's colour, size, lifetime and motion, only drawn with the soft sprite.
     *
     * @param size legacy particle size, in tenths of a block
     */
    public static void definedSmoke(double x, double y, double z, double vx, double vy, double vz, float size,
                                    float r, float g, float b, float a, int age, float gravity, boolean grows) {
        ClientLevel level = level();
        if (level == null || skip(x, y, z)) return;
        int lifetime = age > 0 ? age : 40 + RANDOM.nextInt(20);
        add(level, x, y, z, FxParticle.of(FxSprite.SMOKE)
                .velocity(vx, vy, vz)
                .lifetime(lifetime)
                .size(grows ? size * 0.03F : size * 0.08F, grows ? size * 0.22F : size * 0.14F)
                .color(r, g, b)
                .alpha(Math.min(1, a) * 0.85F)
                .gravity(gravity)
                .spin(spin(0.02F))
                .drag(0.94F));
    }

    /**
     * Stand-ins for the vanilla particle names the definitions use. Returns false for names that
     * have no modern version, so the caller falls back to the vanilla particle.
     */
    public static boolean namedParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = level();
        if (level == null) return false;
        switch (name) {
            case "explode", "largeexplode" -> {
                if (!skip(x, y, z)) {
                    add(level, x, y, z, FxParticle.of(FxSprite.FLAME).velocity(vx, vy, vz)
                            .lifetime(10 + RANDOM.nextInt(6)).size(0.8F, 1.6F).spin(spin(0.05F)).drag(0.9F));
                }
                return true;
            }
            case "hugeexplosion" -> {
                explosion(x, y, z, 4.0F);
                return true;
            }
            case "smoke", "largesmoke", "cloud" -> {
                float grey = name.equals("cloud") ? 0.9F : 0.35F;
                exhaust(x, y, z, vx, vy, vz, name.equals("smoke") ? 0.6F : 1.2F, grey);
                return true;
            }
            case "flame" -> {
                if (!skip(x, y, z)) {
                    add(level, x, y, z, FxParticle.of(FxSprite.FLAME).velocity(vx, vy, vz)
                            .lifetime(8 + RANDOM.nextInt(6)).size(0.3F, 0.5F).spin(spin(0.05F)));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // Helpers

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    private static void add(ClientLevel level, double x, double y, double z, FxParticle.Builder builder) {
        Minecraft.getInstance().particleEngine.add(builder.build(level, x, y, z));
    }

    /** 1.0 nearby, falling to 0.15 far away, multiplied by the player's particle setting. */
    private static float budget(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        ParticleStatus status = mc.options.particles().get();
        float setting = switch (status) {
            case ALL -> 1.0F;
            case DECREASED -> 0.45F;
            case MINIMAL -> 0.15F;
        };
        Camera camera = mc.gameRenderer.getMainCamera();
        double dist = Math.sqrt(camera.getPosition().distanceToSqr(x, y, z));
        float distance = dist <= NEAR_DISTANCE ? 1.0F
                : dist >= FAR_DISTANCE ? 0.15F
                : (float) Mth.lerp((dist - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 1.0, 0.15);
        return setting * distance;
    }

    private static int count(float base, float budget) {
        float n = base * budget;
        int whole = (int) n;
        return whole + (RANDOM.nextFloat() < n - whole ? 1 : 0);
    }

    /** For continuous emitters: randomly drops puffs according to the budget. */
    private static boolean skip(double x, double y, double z) {
        return RANDOM.nextFloat() > budget(x, y, z);
    }

    private static double[] randomDirection(double speed) {
        double theta = RANDOM.nextDouble() * Math.PI * 2;
        double u = RANDOM.nextDouble() * 2 - 1;
        double s = Math.sqrt(1 - u * u);
        double k = speed * (0.5 + RANDOM.nextDouble() * 0.5);
        return new double[]{Math.cos(theta) * s * k, u * k, Math.sin(theta) * s * k};
    }

    private static double jitter(double amount) {
        return (RANDOM.nextDouble() - 0.5) * 2 * amount;
    }

    private static float spin(float max) {
        return (RANDOM.nextFloat() - 0.5F) * 2 * max;
    }
}
