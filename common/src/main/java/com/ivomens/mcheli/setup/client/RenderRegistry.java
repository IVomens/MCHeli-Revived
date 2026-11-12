package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.aircraft.client.LockOnMarkerRenderer;
import com.ivomens.mcheli.block.client.DraftingTableRenderer;
import com.ivomens.mcheli.block.ModBlockEntities;
import com.ivomens.mcheli.client.model.IItemModelRenderer;
import com.ivomens.mcheli.combat.CombatEntityTypes;
import com.ivomens.mcheli.combat.countermeasure.client.ChaffRenderer;
import com.ivomens.mcheli.combat.countermeasure.client.FlareRenderer;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.projectile.client.ProjectileRenderer;
import com.ivomens.mcheli.combat.strike.client.A10StrikeRenderer;
import com.ivomens.mcheli.combat.weapon.client.CartridgeRenderer;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentId;
import com.ivomens.mcheli.entity.client.NullRenderer;
import com.ivomens.mcheli.equipment.EquipmentEntityTypes;
import com.ivomens.mcheli.equipment.gltd.client.GltdRenderer;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.equipment.throwable.client.ThrowableRenderer;
import com.ivomens.mcheli.registry.McheliBlocks;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.setup.client.item.BuiltInDraftingTableItemRenderer;
import com.ivomens.mcheli.setup.client.item.BuiltInGLTDItemRenderer;
import com.ivomens.mcheli.setup.client.item.BuiltInInvisibleItemRenderer;
import com.ivomens.mcheli.setup.client.item.BuiltInLightWeaponItemRenderer;
import com.ivomens.mcheli.setup.client.item.BuiltInRangeFinderItemRenderer;
import com.ivomens.mcheli.setup.client.item.BuiltInWrenchItemRenderer;
import com.ivomens.mcheli.setup.client.item.CustomItemStackRenderer;
import com.ivomens.mcheli.setup.client.item.ItemModelRenderers;
import com.ivomens.mcheli.vehicle.client.debug.DebugSeatRenderer;
import com.ivomens.mcheli.vehicle.client.render.VehicleEntityRenderer;
import com.ivomens.mcheli.vehicle.client.render.VehicleRenderBackend;
import com.ivomens.mcheli.world.WorldEntityTypes;
import com.ivomens.mcheli.world.cargo.client.ContainerRenderer;
import com.ivomens.mcheli.world.chain.client.ChainRenderer;
import com.ivomens.mcheli.world.gunner.client.GunnerRenderer;
import com.ivomens.mcheli.aircraft.parachute.client.ParachuteRenderer;
import com.ivomens.mcheli.world.uavstation.client.UavStationRenderer;
import com.ivomens.mcheli.client.render.ThermalVision;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Client render registration, called from the loader's client setup. */
public final class RenderRegistry {

    private RenderRegistry() {
    }

    private static <E extends AircraftEntity> EntityRendererProvider<E> vehicleRenderer() {
        ClientSession session = McheliClient.session();
        return VehicleEntityRenderer.provider(session.thermalVision(), session.vehicleTextures(), session::vehicleDrawn);
    }

    public static void registerEntityRenderers(RendererRegistrar registrar) {
        ThermalVision thermal = McheliClient.session().thermalVision();
        registrar.registerEntityRenderer(AircraftEntityTypes.SEAT.get(), DebugSeatRenderer.factory(thermal, 0.0F,
                0.3125F, 0.0F, "seat"));
        registrar.registerEntityRenderer(AircraftEntityTypes.HELI.get(), vehicleRenderer());
        registrar.registerEntityRenderer(AircraftEntityTypes.PLANE.get(), vehicleRenderer());
        registrar.registerEntityRenderer(AircraftEntityTypes.SHIP.get(), vehicleRenderer());
        registrar.registerEntityRenderer(AircraftEntityTypes.TANK.get(), vehicleRenderer());
        registrar.registerEntityRenderer(AircraftEntityTypes.VEHICLE.get(), vehicleRenderer());
        VehicleRenderBackend.registerVisualizers();
        registrar.registerEntityRenderer(EquipmentEntityTypes.GLTD.get(), GltdRenderer.factory(thermal));
        registrar.registerEntityRenderer(WorldEntityTypes.CHAIN.get(), ChainRenderer.factory(thermal));
        registrar.registerEntityRenderer(AircraftEntityTypes.PARACHUTE.get(), ParachuteRenderer.factory(thermal));
        registrar.registerEntityRenderer(WorldEntityTypes.CONTAINER.get(), ContainerRenderer.factory(thermal));
        registrar.registerEntityRenderer(WorldEntityTypes.UAV_STATION.get(), UavStationRenderer.factory(thermal));
        registrar.registerEntityRenderer(AircraftEntityTypes.HIDE.get(), NullRenderer::new);
        registrar.registerEntityRenderer(AircraftEntityTypes.HITBOX.get(), NullRenderer::new);
        for (ProjectileKind kind : ProjectileKind.values()) {
            registrar.registerEntityRenderer(kind.entityType(), ProjectileRenderer.factory(thermal, kind));
        }
        registrar.registerEntityRenderer(CombatEntityTypes.A10.get(), A10StrikeRenderer.factory(thermal));
        registrar.registerEntityRenderer(CombatEntityTypes.FLARE.get(), FlareRenderer.factory(thermal));
        registrar.registerEntityRenderer(CombatEntityTypes.CHAFF.get(),
                ChaffRenderer.factory(thermal, LockOnMarkerRenderer::renderInFrame));
        registrar.registerEntityRenderer(EquipmentEntityTypes.THROWABLE.get(), ThrowableRenderer.factory(thermal));
        registrar.registerEntityRenderer(WorldEntityTypes.GUNNER.get(), GunnerRenderer.FACTORY);
        registrar.registerEntityRenderer(AircraftEntityTypes.CARTRIDGE.get(), CartridgeRenderer.factory(thermal));
    }

    public static void registerBlockEntityRenderers(RendererRegistrar registrar) {
        registrar.registerBlockEntityRenderer(ModBlockEntities.DRAFTING_TABLE.get(), DraftingTableRenderer::new);
    }

    /** The loader's renderer registration, e.g. a wrapper around its register-renderers event. */
    public interface RendererRegistrar {
        <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                EntityRendererProvider<T> provider);

        <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                                                 BlockEntityRendererProvider<T> provider);
    }

    /**
     * Sets up the item model renderers and hands over the items drawn by {@link CustomItemStackRenderer}
     * instead of a baked model.
     */
    public static void registerItemRenderers(ItemRendererRegistrar registrar) {
        registerRenderer(McheliItems.JAVELIN.get(), new BuiltInLightWeaponItemRenderer());
        registerRenderer(McheliItems.STINGER.get(), new BuiltInLightWeaponItemRenderer());
        registerRenderer(McheliItems.INVISIBLE.get(), new BuiltInInvisibleItemRenderer());
        registerRenderer(McheliItems.GLTD.get(), new BuiltInGLTDItemRenderer());
        registerRenderer(McheliItems.WRENCH.get(), new BuiltInWrenchItemRenderer());
        registerRenderer(McheliItems.RANGEFINDER.get(), new BuiltInRangeFinderItemRenderer(McheliClient.session().rangeFinder()));
        registerRenderer(McheliBlocks.DRAFTING_TABLE.get(), new BuiltInDraftingTableItemRenderer());

        Set<Item> items = new LinkedHashSet<>(ItemModelRenderers.getRenderers().keySet());
        // aircraft / vehicle / throwable items are created from content at runtime
        VehicleTypes.registry(VehicleKind.HELICOPTER).values().forEach(c -> addContentItem(items, c.id()));
        VehicleTypes.registry(VehicleKind.PLANE).values().forEach(c -> addContentItem(items, c.id()));
        VehicleTypes.registry(VehicleKind.TANK).values().forEach(c -> addContentItem(items, c.id()));
        VehicleTypes.registry(VehicleKind.VEHICLE).values().forEach(c -> addContentItem(items, c.id()));
        VehicleTypes.registry(VehicleKind.SHIP).values().forEach(c -> addContentItem(items, c.id()));
        Throwables.registry().values().forEach(c -> addContentItem(items, Throwables.id(c)));
        if (!items.isEmpty()) {
            registrar.register(CustomItemStackRenderer::getInstance, items);
        }
    }

    /** The loader's custom item renderer registration. */
    public interface ItemRendererRegistrar {
        /**
         * The renderer is fetched when an item is first drawn, not here: it needs the client's block entity
         * renderers and entity models, which do not exist yet at registration time.
         */
        void register(Supplier<? extends BlockEntityWithoutLevelRenderer> renderer, Collection<Item> items);
    }

    private static void registerRenderer(Object item, IItemModelRenderer renderer) {
        ItemModelRenderers.registerRenderer(ItemModelRenderers.asItem(item), renderer);
    }

    private static void addContentItem(Set<Item> items, ContentId content) {
        ContentBindings.item(content).ifPresent(items::add);
    }
}
