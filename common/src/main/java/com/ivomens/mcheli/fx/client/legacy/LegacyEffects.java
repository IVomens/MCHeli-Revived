package com.ivomens.mcheli.fx.client.legacy;

import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The legacy effects: the mod's own textured smoke, splash and flash particles plus vanilla particles
 * looked up by their 1.12 names. Particles are added straight to the particle engine and need no
 * registered particle type.
 */
public class LegacyEffects implements Effects {

    /** Particles farther than this from the camera are not spawned. */
    private static final double MAX_SPAWN_DISTANCE = 300.0;
    /** The block itself, then its four horizontal neighbours. */
    private static final int[][] DUST_OFFSETS = {{0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};


    @Override
    public void spawn(ParticleParams p) {
        if (!p.world().isClientSide()) {
            return;
        }
        BaseParticle particle = p.name().equalsIgnoreCase("Splash")
                ? new McheliSplashParticle(p.world(), p.x(), p.y(), p.z(), p.motionX(), p.motionY(), p.motionZ())
                : new McheliSmokeParticle(p.world(), p.x(), p.y(), p.z(), p.motionX(), p.motionY(), p.motionZ());
        particle.setRBGColorF(p.red(), p.green(), p.blue());
        particle.setAlphaF(p.alpha());
        if (p.age() > 0) {
            particle.setParticleMaxAge(p.age());
        }
        particle.risesAfter = p.risesAfter();
        particle.gravity = p.gravity();
        particle.windBlown = p.windBlown();
        particle.diffusible = p.diffusible();
        particle.turnsWhite = p.turnsWhite();
        if (p.diffusible()) {
            // diffusing smoke starts small and grows to ten times that
            particle.setParticleScale(p.size() * 0.2F);
            particle.particleMaxScale = p.size() * 2.0F;
        } else {
            particle.setParticleScale(p.size());
        }
        Minecraft.getInstance().particleEngine.add(particle);
    }

    @Override
    public void vanilla(String name, double x, double y, double z, double mx, double my, double mz, int... params) {
        createVanilla(name, x, y, z, mx, my, mz, params);
    }

    /**
     * Spawns a vanilla particle by its old name, honouring the particle setting and a maximum distance
     * from the camera; the particle, or null when none was made or this style drew its own.
     */
    @Nullable
    private Particle createVanilla(String type, double x, double y, double z, double mx, double my, double mz,
                                   int... params) {
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        ClientLevel level = mc.level;
        if (view == null || level == null || isSkippedBySetting(mc, level)) {
            return null;
        }
        double dx = view.getX() - x;
        double dy = view.getY() - y;
        double dz = view.getZ() - z;
        if (dx * dx + dy * dy + dz * dz > MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE) {
            return null;
        }

        String key = type.toLowerCase(Locale.ROOT);
        if (drawsNamed(key, x, y, z, mx, my, mz)) {
            return null;
        }
        if (key.startsWith("blockdust")) {
            BlockDustParticle fx = new BlockDustParticle.Factory().createParticle(level, x, y, z, mx, my, mz, params);
            if (fx != null) mc.particleEngine.add(fx);
            return fx;
        }

        ParticleOptions options = toOptions(key, mx, my, mz, params);
        if (options == null) {
            return null;
        }
        // coloured spell / dust particles took their colour from the motion arguments
        boolean colourInMotion = key.equals("mobspell") || key.equals("mobspellambient") || key.equals("reddust");
        return mc.particleEngine.createParticle(options, x, y, z, colourInMotion ? 0.0 : mx, colourInMotion ? 0.0 : my,
                colourInMotion ? 0.0 : mz);
    }

    /** Lets a style draw a named vanilla particle its own way; true if it did. */
    protected boolean drawsNamed(String key, double x, double y, double z, double mx, double my, double mz) {
        return false;
    }

    /** Minimal particles spawn none of these; decreased drops a third of them at random. */
    private static boolean isSkippedBySetting(Minecraft mc, ClientLevel level) {
        ParticleStatus status = mc.options.particles().get();
        return status == ParticleStatus.MINIMAL || status == ParticleStatus.DECREASED && level.random.nextInt(3) == 0;
    }

    @Nullable
    private static ParticleOptions toOptions(String key, double mx, double my, double mz, int... params) {
        return switch (key) {
            case "hugeexplosion" -> ParticleTypes.EXPLOSION_EMITTER;
            case "largeexplode" -> ParticleTypes.EXPLOSION;
            case "fireworksspark" -> ParticleTypes.FIREWORK;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "suspended" -> ParticleTypes.UNDERWATER;
            case "depthsuspend", "townaura" -> ParticleTypes.MYCELIUM;
            case "crit" -> ParticleTypes.CRIT;
            case "magiccrit" -> ParticleTypes.ENCHANTED_HIT;
            case "smoke" -> ParticleTypes.SMOKE;
            case "mobspell" -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, (float) mx, (float) my, (float) mz);
            case "mobspellambient" -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT,
                    FastColor.ARGB32.colorFromFloat(0.15F, (float) mx, (float) my, (float) mz));
            case "spell" -> ParticleTypes.EFFECT;
            case "instantspell" -> ParticleTypes.INSTANT_EFFECT;
            case "witchmagic" -> ParticleTypes.WITCH;
            case "note" -> ParticleTypes.NOTE;
            case "portal" -> ParticleTypes.PORTAL;
            case "enchantmenttable" -> ParticleTypes.ENCHANT;
            case "explode" -> ParticleTypes.POOF;
            case "flame" -> ParticleTypes.FLAME;
            case "lava" -> ParticleTypes.LAVA;
            case "splash" -> ParticleTypes.SPLASH;
            case "wake" -> ParticleTypes.FISHING;
            case "largesmoke" -> ParticleTypes.LARGE_SMOKE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "reddust" -> new DustParticleOptions(new Vector3f(mx == 0.0 ? 1.0F : (float) mx, (float) my, (float) mz), 1.0F);
            case "snowballpoof" -> ParticleTypes.ITEM_SNOWBALL;
            case "dripwater" -> ParticleTypes.DRIPPING_WATER;
            case "driplava" -> ParticleTypes.DRIPPING_LAVA;
            case "snowshovel" -> ParticleTypes.SNOWFLAKE;
            case "slime" -> ParticleTypes.ITEM_SLIME;
            case "heart" -> ParticleTypes.HEART;
            case "angryvillager" -> ParticleTypes.ANGRY_VILLAGER;
            case "happyvillager" -> ParticleTypes.HAPPY_VILLAGER;
            default -> {
                if (key.startsWith("iconcrack") && params.length > 0) {
                    Item item = Item.byId(params[0]);
                    yield new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item));
                } else if (key.startsWith("blockcrack") && params.length > 0) {
                    yield new BlockParticleOption(ParticleTypes.BLOCK, Block.stateById(params[0]));
                }
                // "footstep" and unknown names have no current equivalent
                yield null;
            }
        };
    }

    /** The block state id of a loaded, non-air block, or -1. */
    private static int blockStateId(Level level, int blockX, int blockY, int blockZ) {
        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        if (!level.isLoaded(pos)) {
            return -1;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? -1 : Block.getId(state);
    }

    @Override
    public void blockCrack(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                           double mx, double my, double mz) {
        int stateId = blockStateId(level, blockX, blockY, blockZ);
        if (stateId >= 0) {
            createVanilla("blockcrack", x, y, z, mx, my, mz, stateId);
        }
    }

    @Override
    public boolean blockDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
                             double mx, double my, double mz, float scale) {
        for (int[] offset : DUST_OFFSETS) {
            int stateId = blockStateId(level, blockX + offset[0], blockY + offset[1], blockZ + offset[2]);
            if (stateId >= 0
                    && createVanilla("blockdust", x, y, z, mx, my, mz, stateId) instanceof BlockDustParticle dust) {
                dust.setScale(scale * 2.0F);
                return true;
            }
        }
        return false;
    }

    @Override
    public void muzzleFlash(Level level, double x, double y, double z, float size, float r, float g, float b,
                            float a, int age) {
        ExplosionParticle flash = new ExplosionParticle(level, x, y, z, size, age);
        flash.setParticleMaxAge(age);
        flash.setRBGColorF(r, g, b);
        flash.setAlphaF(a);
        Minecraft.getInstance().particleEngine.add(flash);
    }

    @Override
    public void tintedCloud(double x, double y, double z, float r, float g, float b, float spread,
                            RandomSource random) {
        Particle cloud = createVanilla("cloud", x, y, z, 0, 0, 0);
        if (cloud != null) {
            cloud.setColor(r, g, b);
            cloud.setParticleSpeed((random.nextFloat() - 0.5f) * spread, 0.01f + random.nextFloat() * 0.02f,
                    (random.nextFloat() - 0.5f) * spread);
        }
    }

    @Override
    public void bulletDebris(Level level, Vec3 at, BlockPos block, float width, int cracks, float spread, int clouds,
                             RandomSource random) {
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        BlockState state = level.getBlockState(block);
        if (!state.isAir()) {
            int crackCount = cracks + random.nextInt(3);
            BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, state);
            for (int i = 0; i < crackCount; i++) {
                double mx = (spread / 2.0F) * random.nextGaussian();
                double mz = (spread / 2.0F) * random.nextGaussian();
                double my = spread * Math.abs(random.nextGaussian());
                Particle fx = engine.createParticle(crack, at.x + (random.nextFloat() - 0.5D) * width, at.y + 0.1D,
                        at.z + (random.nextFloat() - 0.5D) * width, mx, my, mz);
                if (fx != null) {
                    fx.setParticleSpeed(mx, my, mz);
                }
            }
        }

        for (int i = 0; i < clouds; i++) {
            engine.createParticle(ParticleTypes.CLOUD, at.x + random.nextGaussian(), at.y + random.nextGaussian(),
                    at.z + random.nextGaussian(), random.nextGaussian() / 200.0D, random.nextGaussian() / 200.0D,
                    random.nextGaussian() / 200.0D);
        }
    }

    @Override
    public void ironCurtainSparks(Level level, Vec3 at, BlockPos block, float width, float height, int cracks,
                                  float spread, RandomSource random) {
        final float r = 0.5F, g = 0.1F, b = 0.1F;
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        BlockState state = level.getBlockState(block);

        if (!state.isAir()) {
            BlockParticleOption crack = new BlockParticleOption(ParticleTypes.BLOCK, state);
            int crackNum = cracks + random.nextInt(3);
            for (int i = 0; i < crackNum; i++) {
                double mx = spread * (random.nextGaussian() * 0.5);
                double mz = spread * (random.nextGaussian() * 0.5);
                double my = spread * Math.abs(random.nextGaussian());
                Particle fx = engine.createParticle(crack, at.x + (random.nextFloat() - 0.5D) * width, at.y + 0.1D,
                        at.z + (random.nextFloat() - 0.5D) * width, mx, my, mz);
                if (fx != null) {
                    fx.setColor(r, g, b);
                    fx.scale(0.8F);
                    fx.setParticleSpeed(mx, my, mz);
                }
            }
        }

        int cloudNum = 50 + (int) spread;
        for (int i = 0; i < cloudNum; i++) {
            Particle cloud = engine.createParticle(ParticleTypes.CLOUD, at.x + (random.nextFloat() - 0.5D) * width,
                    at.y + random.nextGaussian() * height, at.z + (random.nextFloat() - 0.5D) * width,
                    random.nextGaussian() / 100, random.nextGaussian() / 100, random.nextGaussian() / 100);
            if (cloud != null) {
                cloud.setColor(r, g, b);
            }
        }
    }

}
