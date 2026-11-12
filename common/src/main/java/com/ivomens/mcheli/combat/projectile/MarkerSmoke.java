package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.fx.Effects;
import com.ivomens.mcheli.fx.ParticleParams;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The marker rocket: where it lands it puffs coloured smoke and, after a countdown, a stick of bombs
 * falls from high above. It only calls the strike in if the sky above the marker is mostly open.
 */
final class MarkerSmoke implements RoleBehaviour {

    static final byte INACTIVE = 0;
    static final byte FLYING = 1;
    static final byte MARKING = 2;

    private static final int MARKING_TICKS = 100;
    /** The bombs are released when the countdown reaches this value. */
    private static final int BOMB_RELEASE_TICK = 40;
    private static final float BOMB_RELEASE_ALTITUDE = 260.0F;
    private static final float BOMB_SPREAD = 15.0F;
    /** The marker is refused when this many solid blocks are above it. */
    private static final int MAX_BLOCKS_OVERHEAD = 5;
    private static final int SMOKE_PUFFS = 5;

    private final ProjectileEntity rocket;
    private int countDown;

    MarkerSmoke(ProjectileEntity rocket) {
        this.rocket = rocket;
    }

    private byte status() {
        return this.rocket.roleState();
    }

    @Override
    public boolean tickInstead() {
        if (this.rocket.level().isClientSide) {
            this.tickClient();
        } else {
            this.tickServer();
        }
        return true;
    }

    private void tickClient() {
        byte status = this.status();
        if (this.rocket.spec() == null) {
            this.rocket.flyOneTick();
        }
        WeaponSpec info = this.rocket.spec();
        if (info == null || info.render().smoke().disabled() || status == INACTIVE) {
            return;
        }
        if (status == FLYING) {
            this.rocket.flyOneTick();
            Trail.puffs(this.rocket, info.render().trajectoryParticle(), 3, 2.5F * info.render().smoke().size());
        } else {
            RandomSource random = this.rocket.getRandom();
            float greenBlue = random.nextFloat() * 0.3F;
            this.puffSmoke(10 + random.nextInt(4), random.nextFloat() * 0.2F + 0.8F, greenBlue, greenBlue,
                    (random.nextFloat() - 0.5F) * 0.7F, 0.3F + random.nextFloat() * 0.3F,
                    (random.nextFloat() - 0.5F) * 0.7F);
        }
    }

    private void tickServer() {
        byte status = this.status();
        if (status == INACTIVE || this.rocket.isInWater()) {
            this.rocket.discard();
        } else if (status == FLYING) {
            this.rocket.flyOneTick();
        } else if (this.countDown > 0) {
            if (--this.countDown == BOMB_RELEASE_TICK) {
                this.releaseBombs();
            }
        } else {
            this.rocket.discard();
        }
    }

    private void releaseBombs() {
        RandomSource random = this.rocket.getRandom();
        int count = 6 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            Vec3 at = new Vec3(this.rocket.getX() + (random.nextFloat() - 0.5F) * BOMB_SPREAD,
                    BOMB_RELEASE_ALTITUDE + random.nextFloat() * 10.0F + i * 30,
                    this.rocket.getZ() + (random.nextFloat() - 0.5F) * BOMB_SPREAD);
            ProjectileEntity bomb = ProjectileEntity.create(ProjectileKind.BOMB, this.rocket.level(), at,
                    new Vec3(0.0, -0.5, 0.0), 0.0F, 90.0F, 4.0);
            bomb.setWeapon(this.rocket.weaponName());
            bomb.warhead().set(30, 3 + random.nextInt(2), 0, 0);
            bomb.setShooter(this.rocket.shooter(), this.rocket.platform());
            this.rocket.level().addFreshEntity(bomb);
        }
    }

    /** Client: wind-blown smoke puffs along the last tick's movement. */
    private void puffSmoke(float size, float r, float g, float b, float mx, float my, float mz) {
        ProjectileEntity p = this.rocket;
        double stepX = (p.getX() - p.xo) / SMOKE_PUFFS;
        double stepY = (p.getY() - p.yo) / SMOKE_PUFFS;
        double stepZ = (p.getZ() - p.zo) / SMOKE_PUFFS;
        for (int i = 0; i < SMOKE_PUFFS; i++) {
            ParticleParams prm = new ParticleParams(p.level(), "smoke", p.xo + stepX * i, p.yo + stepY * i, p.zo + stepZ * i);
            prm.setMotion(mx, my, mz);
            prm.setSize(size + p.getRandom().nextFloat());
            prm.setColor(1.0F, r, g, b);
            prm.setWindBlown(true);
            Effects.get().spawn(prm);
        }
    }

    @Override
    public boolean onImpact(HitResult hit) {
        if (this.rocket.level().isClientSide) {
            return true;
        }
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            this.startMarking(entity.getX(), entity.getY() + entity.getBbHeight() + 1.1, entity.getZ());
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            this.markBlock(blockHit);
        }
        return true;
    }

    private void markBlock(BlockHitResult hit) {
        BlockPos markerPos = hit.getBlockPos().relative(hit.getDirection());
        Level level = this.rocket.level();
        if (!level.isEmptyBlock(markerPos)) {
            this.rocket.discard();
            return;
        }
        if (McheliConfig.combat().explosionFlamingBlock()) {
            level.setBlockAndUpdate(markerPos, BaseFireBlock.getState(level, markerPos));
        }
        if (countBlocksOverhead(level, markerPos) >= MAX_BLOCKS_OVERHEAD) {
            this.rocket.discard();
            return;
        }
        this.startMarking(markerPos.getX() + 0.5, markerPos.getY() + 1.1, markerPos.getZ() + 0.5);
    }

    private static int countBlocksOverhead(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight() && count < MAX_BLOCKS_OVERHEAD; y++) {
            if (!level.isEmptyBlock(cursor.set(pos.getX(), y, pos.getZ()))) {
                count++;
            }
        }
        return count;
    }

    private void startMarking(double x, double y, double z) {
        ProjectileEntity p = this.rocket;
        p.setRoleState(MARKING);
        p.setPos(x, y, z);
        p.xo = x;
        p.yo = y;
        p.zo = z;
        p.setDeltaMovement(Vec3.ZERO);
        p.hasImpulse = true;
        this.countDown = MARKING_TICKS;
    }
}
