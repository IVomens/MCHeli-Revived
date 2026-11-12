package com.ivomens.mcheli.tank;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.HitBoxEntity;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.entityview.TowChain;
import com.ivomens.mcheli.combat.HurtTime;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.config.BreakRules;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.vehicle.component.CollisionExclusions;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server: a moving tank runs over what is in its way. Its hull parts break blocks (with
 * Collision_DestroyBlock) and ram entities, which pushes and hurts them; light vehicles take some
 * of the damage back.
 */
final class TankRamming {

    private static final byte UNKNOWN = 0;
    private static final byte PASS = 1;
    private static final byte BREAK = 2;
    private static final byte BREAK_AND_STOP = 3;
    private static final byte STOP = 4;

    private final TankEntity tank;
    /**
     * What the break rules say about each block state met this tick. Cleared every tick, so a config
     * reload or a tag change is seen on the next one, as it was when every block was looked up.
     */
    private final Reference2ByteOpenHashMap<BlockState> verdicts = new Reference2ByteOpenHashMap<>();

    TankRamming(TankEntity tank) {
        this.tank = tank;
        this.verdicts.defaultReturnValue(UNKNOWN);
    }

    /** Each hull part is checked every third tick on average, to spread the cost. */
    void tick() {
        TankEntity t = this.tank;
        if (t.spec() == null) {
            return;
        }
        t.wheels().updateBlock();
        this.verdicts.clear();
        boolean destroyBlocks = McheliConfig.combat().collisionDestroyBlock();
        for (PartBoundingBox box : t.partBoxes().boxes()) {
            if (t.getRandom().nextInt(3) == 0) {
                if (destroyBlocks) {
                    breakBlocks(t.body().toWorld(box.offsetX(), box.offsetY(), box.offsetZ()), box.width(),
                            box.height());
                }
                ram(box.boundingBox());
            }
        }
        if (destroyBlocks) {
            breakBlocks(t.body().toWorld(0.0, 0.0, 0.0), t.getBbWidth() * 1.5, t.getBbHeight() * 2.0F);
        }
        ram(t.getBoundingBox());
    }

    private void ram(@Nullable AABB box) {
        if (box == null) {
            return;
        }
        TankEntity t = this.tank;
        Vec3 motion = t.getDeltaMovement();
        double speed = motion.length();
        if (speed <= 0.05) {
            return;
        }

        Entity rider = t.getRiddenByEntity();
        float damage = (float) (speed * 15.0);
        Entity vehicle = t.getVehicle();
        AircraftEntity carrier = vehicle instanceof SeatEntity seat ? seat.getParent()
                : vehicle instanceof AircraftEntity aircraft ? aircraft : null;
        Level level = t.level();
        List<Entity> hit = level.getEntities(t, box.inflate(0.3), entity -> canRam(entity, carrier));
        for (Entity entity : hit) {
            if (!shouldHurt(entity)) {
                continue;
            }
            double dx = entity.getX() - t.getX();
            double dz = entity.getZ() - t.getZ();
            double distance = Math.min(Math.sqrt(dx * dx + dz * dz), 5.0);
            // damage accumulates over everything hit this tick
            damage = (float) (damage + (5.0 - distance));
            DamageSource source = rider instanceof LivingEntity living ? level.damageSources().mobAttack(living)
                    : level.damageSources().generic();
            HurtTime.scale(entity);
            entity.hurt(source, damage);
            if (entity instanceof AircraftEntity) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(motion.x * 0.05, 0.0, motion.z * 0.05));
            } else if (entity instanceof AbstractArrow) {
                entity.discard();
            } else {
                entity.setDeltaMovement(entity.getDeltaMovement().add(motion.x * 1.5, 0.0, motion.z * 1.5));
            }

            if (!t.isHeavy() && (entity.getBbWidth() >= 1.0F || entity.getBbHeight() >= 1.5)) {
                DamageSource recoil = entity instanceof LivingEntity living ? level.damageSources().mobAttack(living)
                        : level.damageSources().generic();
                t.hurt(recoil, damage / 3.0F);
            }
        }
    }

    private static boolean canRam(Entity entity, @Nullable AircraftEntity carrier) {
        if (entity == carrier || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || entity instanceof ProjectileEntity || entity instanceof TowChain || entity instanceof SeatEntity) {
            return false;
        }
        if (entity instanceof TankEntity other && other.tankSpec() != null && other.isHeavy()) {
            return McheliConfig.combat().collisionEntityTankDamage();
        }
        return McheliConfig.combat().collisionEntityDamage();
    }

    /** The crew, and whatever the tank ignores in collision (with its parts and riders), is spared. */
    private boolean shouldHurt(Entity entity) {
        CollisionExclusions ignored = this.tank.collisionExclusions();
        if (this.tank.seats().seatIdOf(entity) >= 0 || ignored.ignores(entity)) {
            return false;
        }
        if (entity instanceof HitBoxEntity hitBox && hitBox.owner() != null && ignored.ignores(hitBox.owner())) {
            return false;
        }
        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof AircraftEntity && ignored.ignores(vehicle)) {
            return false;
        }
        return !(vehicle instanceof SeatEntity seat) || seat.getParent() == null || !ignored.ignores(seat.getParent());
    }

    /**
     * Breaks blocks in a box around {@code center}. Each column is scanned upwards and stops at the
     * first unbreakable block or block from the explicit break list, so walls only crumble from the bottom.
     */
    private void breakBlocks(Vec3 center, double width, double height) {
        TankEntity t = this.tank;
        BreakRules.VehicleBreakRules rules = McheliConfig.combat().blocks().forWeightType(t.tankSpec().file().kindFeatures().weightType());
        int halfWidth = (int) (width + 2.0) / 2;
        int halfHeight = (int) (height + 2.0) / 2;
        Level level = t.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                int blockX = Mth.floor(center.x + x - 0.5);
                int blockZ = Mth.floor(center.z + z - 0.5);
                // whether the chunk is there depends on the column only; the level reads blocks from it
                if (!level.hasChunkAt(blockX, blockZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(blockX),
                        SectionPos.blockToSectionCoord(blockZ));
                breakColumn(rules, chunk, pos, blockX, blockZ, center.y, halfHeight);
            }
        }
    }

    /** One column of {@link #breakBlocks}, from the bottom up. */
    private void breakColumn(BreakRules.VehicleBreakRules rules, LevelChunk chunk, BlockPos.MutableBlockPos pos,
            int blockX, int blockZ, double centerY, int halfHeight) {
        Level level = this.tank.level();
        for (int y = -halfHeight; y <= halfHeight + 1; y++) {
            int blockY = Mth.floor(centerY + y - 1.0);
            if (level.isOutsideBuildHeight(blockY)) {
                continue;
            }
            pos.set(blockX, blockY, blockZ);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            byte verdict = verdict(rules, state);
            if (verdict == STOP) {
                break;
            }
            if (verdict != PASS) {
                breakBlock(pos.immutable());
                if (verdict == BREAK_AND_STOP) {
                    break;
                }
            }
        }
    }

    /** What the rules say about a block state; worked out once per state and tick. */
    private byte verdict(BreakRules.VehicleBreakRules rules, BlockState state) {
        byte known = this.verdicts.getByte(state);
        if (known != UNKNOWN) {
            return known;
        }
        Block block = state.getBlock();
        byte verdict;
        if (rules.unbreakable().contains(block)) {
            verdict = STOP;
        } else if (rules.breakable().contains(block)) {
            verdict = BREAK_AND_STOP;
        } else {
            verdict = rules.isBreakableMaterial(state) ? BREAK : PASS;
        }
        this.verdicts.put(state, verdict);
        return verdict;
    }

    /** One in eight broken blocks drops as an item. */
    private void breakBlock(BlockPos pos) {
        if (this.tank.getRandom().nextInt(8) == 0) {
            this.tank.level().destroyBlock(pos, true);
        } else {
            this.tank.level().removeBlock(pos, false);
        }
    }
}
