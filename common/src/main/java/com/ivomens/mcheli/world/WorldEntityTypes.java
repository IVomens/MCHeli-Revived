package com.ivomens.mcheli.world;

import com.ivomens.mcheli.registry.EntityTypeRegistration;
import com.ivomens.mcheli.world.cargo.CargoContainerEntity;
import com.ivomens.mcheli.world.chain.ChainEntity;
import com.ivomens.mcheli.world.gunner.GunnerEntity;
import com.ivomens.mcheli.world.uavstation.UavStationEntity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/** The standalone entities placed in the world: chains, cargo, UAV stations and gunners. */
public final class WorldEntityTypes {

    public static final Supplier<EntityType<ChainEntity>> CHAIN =
            EntityTypeRegistration.simple("chain", ChainEntity::new, 1.0F, 1.0F, 10, 10);
    public static final Supplier<EntityType<CargoContainerEntity>> CONTAINER =
            EntityTypeRegistration.simple("container", CargoContainerEntity::new, 2.0F, 1.0F, 10, 10);
    public static final Supplier<EntityType<UavStationEntity>> UAV_STATION =
            EntityTypeRegistration.simple("uav_station", UavStationEntity::new, 2.0F, 0.7F, 10, 10);
    public static final Supplier<EntityType<GunnerEntity>> GUNNER =
            EntityTypeRegistration.simple("gunner", GunnerEntity::new, 0.6F, 1.8F, 10, 3);

    private WorldEntityTypes() {
    }

    /** Loading the class registers the entity types; call once while the mod is constructed. */
    public static void init() {
    }
}
