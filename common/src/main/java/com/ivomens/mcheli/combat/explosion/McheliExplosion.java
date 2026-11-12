package com.ivomens.mcheli.combat.explosion;

import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.combat.HurtTime;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.sound.WorldSounds;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * MCHeli explosion: the classic MCHeli ray pattern ({@link BlockRays}) and damage formula, set up from
 * an {@link ExplosionSpec}.
 * <p>
 * The server runs the whole explosion (({@link #detonate}); a client only replays the entity push of the
 * blocks the server sent ({@link #pushEntities}) and draws the effect with {@code ExplosionEffects}.
 */
public class McheliExplosion extends Explosion {

    private final Level world;
    private final ExplosionSpec spec;
    @Nullable
    private final Entity exploder;
    @Nullable
    private final Player placedBy;
    private final boolean damagesTerrain;
    private boolean hitEntity;

    /** Client side, effect only, with the block positions the server sent. */
    public McheliExplosion(Level world, Vec3 at, float size, List<BlockPos> affectedPositions) {
        this(world, null, null, ExplosionSpec.at(at, size).sound(false).build(), true);
        this.getToBlow().addAll(affectedPositions);
    }

    public McheliExplosion(Level world, @Nullable Entity exploder, @Nullable Entity player, ExplosionSpec spec,
                           boolean damagesTerrain) {
        super(world, exploder, ModDamageSources.explosion(world, exploder, placedBy(exploder, player)), null,
                spec.at().x, spec.at().y, spec.at().z, spec.size(), spec.fire(),
                damagesTerrain ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        this.world = world;
        this.spec = spec;
        this.exploder = exploder;
        this.placedBy = player instanceof Player p ? p : null;
        this.damagesTerrain = damagesTerrain;
    }

    @Nullable
    private static Entity placedBy(@Nullable Entity exploder, @Nullable Entity player) {
        if (player instanceof Player) return player;
        return exploder instanceof LivingEntity ? exploder : null;
    }

    public ExplosionSpec spec() {
        return this.spec;
    }

    public List<BlockPos> getAffectedBlockPositions() {
        return this.getToBlow();
    }

    /** Server: finds the blocks, hurts and pushes the entities, then breaks blocks and lights fires. */
    public ExplosionResult detonate() {
        new BlockRays(this.world, this, this.exploder).collect(this.getToBlow());
        this.damageEntities();
        if (this.spec.sound()) {
            WorldSounds.playExplosionSound(this.world, this.spec.at().x, this.spec.at().y, this.spec.at().z);
        }
        if (this.damagesTerrain) {
            this.breakBlocks();
        }
        if (this.spec.fire() && McheliConfig.combat().explosionFlamingBlock()) {
            this.lightFires();
        }
        return new ExplosionResult(this.hitEntity);
    }

    /** Client: pushes the local entities the way the server pushed its own. */
    public void pushEntities() {
        this.damageEntities();
    }

    private void damageEntities() {
        Vec3 center = this.spec.at();
        float damageRadius = this.spec.damageRadius() > 0.0F ? this.spec.damageRadius() : this.spec.size();
        float reach = damageRadius * 2.0F;
        AABB box = new AABB(Mth.floor(center.x - reach - 1.0), Mth.floor(center.y - reach - 1.0),
                Mth.floor(center.z - reach - 1.0), Mth.floor(center.x + reach + 1.0), Mth.floor(center.y + reach + 1.0),
                Mth.floor(center.z + reach + 1.0));
        List<Entity> entities = this.world.getEntities(this.exploder, box);
        if (!this.world.isClientSide) {
            Services.GAMEPLAY.onExplosionDetonate(this.world, this, entities, reach);
        }
        DamageSource source = null;
        for (Entity entity : entities) {
            if (entity.ignoreExplosion(this)) continue;
            double relativeDist = Math.sqrt(entity.distanceToSqr(center)) / reach;
            if (relativeDist > 1.0) continue;
            if (source == null && !this.world.isClientSide) {
                source = ModDamageSources.explosion(this.world, this, box);
            }
            this.blast(entity, relativeDist, reach, source);
        }
    }

    /** Hurts, pushes and ignites one entity {@code relativeDist} of the reach away from the centre. */
    private void blast(Entity entity, double relativeDist, float reach, @Nullable DamageSource source) {
        Vec3 center = this.spec.at();
        double dx = entity.getX() - center.x;
        double dy = entity.getEyeY() - center.y;
        double dz = entity.getZ() - center.z;
        double eyeDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (eyeDist == 0.0) return;
        dx /= eyeDist;
        dy /= eyeDist;
        dz /= eyeDist;

        BlastCover cover = this.spec.cover();
        double seenPercent = this.seenPercent(center, entity, cover);
        double impact = (1.0 - relativeDist) * (cover.throughWalls() ? 1.0 : seenPercent);
        float damage = (int) ((impact * impact + impact) / 2.0 * 8.0 * this.spec.damagePower() + 1.0);
        if (cover.throughWalls() && seenPercent < 1.0) {
            damage *= Math.max(0.0F, Math.min(1.0F, cover.behindCover()));
        }
        if (source != null) {
            damage = this.hurt(entity, source, damage);
        }
        this.push(entity, impact, dx, dy, dz);
        if (source != null && damage > 0.0F && this.spec.igniteEntities() > 0) {
            double fireFactor = 1.0 - eyeDist / reach;
            if (fireFactor > 0.0) {
                entity.igniteForSeconds((float) (int) (fireFactor * this.spec.igniteEntities()));
            }
        }
    }

    /**
     * How much of the entity the blast sees, from rays to points of its box. A projectile is neither hurt
     * by an explosion nor pushed, and with a damage power of zero or more its damage is at least one
     * whatever it sees; only cover rules that can cut the damage to nothing make the rays matter. They
     * are skipped otherwise, which spares a firefight's blasts the rays to every round flying past.
     */
    private double seenPercent(Vec3 center, Entity entity, BlastCover cover) {
        if (entity instanceof ProjectileEntity && !cover.throughWalls() && this.spec.damagePower() >= 0.0F) {
            return 1.0;
        }
        return Explosion.getSeenPercent(center, entity);
    }

    private float hurt(Entity entity, DamageSource source, float damage) {
        if (damage > 0.0F && this.countsAsHit(entity)) {
            this.hitEntity = true;
        }
        HurtTime.scale(entity);
        float dealt = DamageScaling.vsEntity(entity, source, damage);
        dealt *= this.spec.damageFactor() != null ? this.spec.damageFactor().getDamageFactor(entity) : 1.0F;
        entity.hurt(source, dealt);
        return dealt;
    }

    private void push(Entity entity, double impact, double dx, double dy, double dz) {
        double knockback = impact;
        if (entity instanceof LivingEntity living) {
            knockback = impact * (1.0 - living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
        }
        if (!(entity instanceof ProjectileEntity)) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(dx * knockback * 0.4, dy * knockback * 0.1,
                    dz * knockback * 0.4));
        }
        if (entity instanceof Player player && !player.isSpectator()
                && (!player.isCreative() || !player.getAbilities().flying)) {
            this.getHitPlayers().put(player, new Vec3(dx * impact, dy * impact, dz * impact));
        }
    }

    /** Whether hurting the entity reports a hit to the shooter: not drops, orbs or the shooter's own projectiles. */
    private boolean countsAsHit(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof ThrownExperienceBottle
                || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity) {
            return false;
        }
        return !(entity instanceof ProjectileEntity projectile) || this.placedBy == null
                || idOf(projectile.shooter()) != this.placedBy.getId();
    }

    private static int idOf(@Nullable Entity entity) {
        return entity != null ? entity.getId() : -1;
    }

    /** Breaks the blocks the rays reached, merging their drops like vanilla explosions. */
    private void breakBlocks() {
        boolean breakBlocks = this.spec.destroyBlocks() && this.spec.blockSize() > 0.0F
                && McheliConfig.combat().explosionDestroyBlock();
        if (!breakBlocks) {
            return;
        }
        List<Pair<ItemStack, BlockPos>> drops = new ArrayList<>();
        for (BlockPos blockPos : this.getToBlow()) {
            if (this.world.hasChunkAt(blockPos)) {
                BlockState state = this.world.getBlockState(blockPos);
                if (!state.isAir()) {
                    state.onExplosionHit(this.world, blockPos, this, (stack, p) -> addOrAppendStack(drops, stack, p));
                }
            }
        }
        for (Pair<ItemStack, BlockPos> pair : drops) {
            Block.popResource(this.world, pair.getSecond(), pair.getFirst());
        }
    }

    private void lightFires() {
        for (BlockPos blockPos : this.getToBlow()) {
            if (CombatRandom.of(this.world).blasts().nextInt(3) != 0 || !this.world.hasChunkAt(blockPos)) continue;
            BlockPos below = blockPos.below();
            if (this.world.getBlockState(blockPos).isAir()
                    && this.world.getBlockState(below).isSolidRender(this.world, below)) {
                this.world.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.world, blockPos));
            }
        }
    }

    private static void addOrAppendStack(List<Pair<ItemStack, BlockPos>> drops, ItemStack stack, BlockPos pos) {
        for (int i = 0; i < drops.size(); i++) {
            Pair<ItemStack, BlockPos> pair = drops.get(i);
            ItemStack existing = pair.getFirst();
            if (ItemEntity.areMergable(existing, stack)) {
                drops.set(i, Pair.of(ItemEntity.merge(existing, stack, 16), pair.getSecond()));
                if (stack.isEmpty()) return;
            }
        }
        drops.add(Pair.of(stack, pos));
    }

    @Nullable
    @Override
    public LivingEntity getIndirectSourceEntity() {
        return this.placedBy != null ? this.placedBy : super.getIndirectSourceEntity();
    }
}
