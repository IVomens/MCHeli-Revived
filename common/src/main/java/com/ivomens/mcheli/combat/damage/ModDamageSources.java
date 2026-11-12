package com.ivomens.mcheli.combat.damage;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * MCHeli damage sources. The damage types are data driven ({@code data/mcheli/damage_type/*.json})
 * and tagged {@code minecraft:is_projectile} / {@code minecraft:is_explosion}, so vanilla armor,
 * enchantments and other mods treat them like their vanilla counterparts.
 * <p>
 * The message ids are {@code thrown} and {@code explosion} so the vanilla death messages apply.
 */
public final class ModDamageSources {

    public static final ResourceKey<DamageType> BULLET = key("bullet");
    public static final ResourceKey<DamageType> EXPLOSION = key("explosion");

    private ModDamageSources() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Ids.of(name));
    }

    /** Damage dealt by a bullet, credited to its shooter. */
    public static DamageSource bullet(Level level, Entity bullet, @Nullable Entity shooter) {
        return level.damageSources().source(BULLET, bullet, shooter);
    }

    /** Bullet damage of a weapon's beam, dealt by the platform that fires it and credited to its user. */
    public static DamageSource beam(Level level, Entity platform, @Nullable Entity user, String weaponId) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BULLET);
        return new WeaponDamageSource(type, platform, user, weaponId);
    }

    /**
     * The MCHeli weapon type behind a damage source: a beam's, or that of the projectile that hit or exploded;
     * null for damage of anything else.
     */
    @Nullable
    public static String weaponIdOf(DamageSource source) {
        if (source instanceof WeaponDamageSource weapon) {
            return weapon.weaponId();
        }
        if (source.getDirectEntity() instanceof ProjectileEntity projectile && !projectile.weaponName().isEmpty()) {
            return projectile.weaponName();
        }
        return null;
    }

    /** Explosion damage, credited to whoever placed or fired the explosive. */
    public static DamageSource explosion(Level level, @Nullable Entity exploder, @Nullable Entity placedBy) {
        return level.damageSources().source(EXPLOSION, exploder, placedBy);
    }

    /** Damage from an explosion that looked for its victims in {@code area}. */
    public static DamageSource explosion(Level level, Explosion explosion, AABB area) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(EXPLOSION);
        return new AreaDamageSource(type, explosion.getDirectSourceEntity(), explosion.getIndirectSourceEntity(), area);
    }

    /** True for MCHeli bullets and vanilla thrown projectiles. */
    public static boolean isThrown(DamageSource source) {
        return source.is(BULLET) || source.is(DamageTypes.THROWN);
    }

    public static boolean isExplosion(DamageSource source) {
        return source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
