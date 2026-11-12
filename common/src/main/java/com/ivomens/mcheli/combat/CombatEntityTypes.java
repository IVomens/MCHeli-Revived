package com.ivomens.mcheli.combat;

import com.ivomens.mcheli.combat.countermeasure.ChaffEntity;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.strike.A10StrikeEntity;
import com.ivomens.mcheli.registry.EntityTypeRegistration;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * The projectiles, the A-10 strike, flares and chaff. Every projectile kind keeps the id it always had;
 * all of them are one class (see {@code ProjectileKind}).
 */
public final class CombatEntityTypes {

    public static final Supplier<EntityType<ProjectileEntity>> ROCKET = projectile("rocket");
    public static final Supplier<EntityType<ProjectileEntity>> TV_MISSILE = projectile("tv_missile");
    public static final Supplier<EntityType<ProjectileEntity>> BULLET = projectile("bullet");
    public static final Supplier<EntityType<A10StrikeEntity>> A10 =
            EntityTypeRegistration.projectile("a10", A10StrikeEntity::new);
    public static final Supplier<EntityType<ProjectileEntity>> AA_MISSILE = projectile("aa_missile");
    public static final Supplier<EntityType<ProjectileEntity>> AS_MISSILE = projectile("as_missile");
    public static final Supplier<EntityType<ProjectileEntity>> TORPEDO = projectile("torpedo");
    public static final Supplier<EntityType<ProjectileEntity>> AT_MISSILE = projectile("at_missile");
    public static final Supplier<EntityType<ProjectileEntity>> BOMB = projectile("bomb");
    public static final Supplier<EntityType<ProjectileEntity>> MARKER_ROCKET = projectile("marker_rocket");
    public static final Supplier<EntityType<ProjectileEntity>> DISPENSED_ITEM = projectile("dispensed_item");
    public static final Supplier<EntityType<FlareEntity>> FLARE =
            EntityTypeRegistration.simple("flare", FlareEntity::new, 1.0F, 1.0F, 8, 10);
    public static final Supplier<EntityType<ChaffEntity>> CHAFF =
            EntityTypeRegistration.simple("chaff", ChaffEntity::new, 1.0F, 1.0F, 8, 10);

    private CombatEntityTypes() {
    }

    /** Loading the class registers the entity types; call once while the mod is constructed. */
    public static void init() {
    }

    private static Supplier<EntityType<ProjectileEntity>> projectile(String name) {
        return EntityTypeRegistration.projectile(name, ProjectileEntity::new);
    }
}
