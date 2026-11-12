package com.ivomens.mcheli.registry;

import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.function.Supplier;

/**
 * Builds and registers MCHeli's entity types. Tracking ranges are in chunks and are also capped by the
 * server view distance, so a vehicle is only sent to players that could see it. Player driven vehicles
 * update every tick for smooth interpolation; stations, containers and debris update far less often.
 * Projectiles use a medium range since their owner and target are nearby.
 */
public final class EntityTypeRegistration {

    private EntityTypeRegistration() {
    }

    /** A vehicle: large range, updated every tick for the driver controlled motion. */
    public static <T extends Entity> Supplier<EntityType<T>> vehicle(String name, EntityType.EntityFactory<T> factory) {
        return Services.REGISTRY.register(Registries.ENTITY_TYPE, name, () -> EntityType.Builder
                .of(factory, MobCategory.MISC)
                .sized(2.0F, 0.7F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .fireImmune()
                .noSummon()
                .build(Ids.MOD_ID + ":" + name));
    }

    /** A projectile: medium range, updated every other tick. */
    public static <T extends Entity> Supplier<EntityType<T>> projectile(String name,
                                                                        EntityType.EntityFactory<T> factory) {
        return Services.REGISTRY.register(Registries.ENTITY_TYPE, name, () -> EntityType.Builder
                .of(factory, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(12)
                .updateInterval(2)
                .noSummon()
                .build(Ids.MOD_ID + ":" + name));
    }

    /**
     * An entity its parent places on every tick, on the server and on each client alike, so clients ignore
     * the server's position updates for it: a vehicle's seat. It is sent when it spawns, when its riders or
     * synced data change, and never because it moved; a seat would otherwise cost every observer a move
     * packet every other tick.
     */
    public static <T extends Entity> Supplier<EntityType<T>> placedByParent(String name,
                                                                            EntityType.EntityFactory<T> factory,
                                                                            float width, float height,
                                                                            int rangeChunks) {
        return simple(name, factory, width, height, rangeChunks, Integer.MAX_VALUE);
    }

    /** Any other entity, with its own size, range in chunks and update interval. */
    public static <T extends Entity> Supplier<EntityType<T>> simple(String name, EntityType.EntityFactory<T> factory,
                                                                    float width, float height, int rangeChunks,
                                                                    int interval) {
        return Services.REGISTRY.register(Registries.ENTITY_TYPE, name, () -> EntityType.Builder
                .of(factory, MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(rangeChunks)
                .updateInterval(interval)
                .build(Ids.MOD_ID + ":" + name));
    }
}
