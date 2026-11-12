package com.ivomens.mcheli.equipment;

import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.equipment.throwable.ThrowableEntity;
import com.ivomens.mcheli.registry.EntityTypeRegistration;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/** The laser designator and thrown items. */
public final class EquipmentEntityTypes {

    public static final Supplier<EntityType<GltdEntity>> GLTD =
            EntityTypeRegistration.simple("gltd", GltdEntity::new, 0.5F, 0.5F, 10, 10);
    public static final Supplier<EntityType<ThrowableEntity>> THROWABLE =
            EntityTypeRegistration.simple("throwable", ThrowableEntity::new, 0.25F, 0.25F, 8, 10);

    private EquipmentEntityTypes() {
    }

    /** Loading the class registers the entity types; call once while the mod is constructed. */
    public static void init() {
    }
}
