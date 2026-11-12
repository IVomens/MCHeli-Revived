package com.ivomens.mcheli.setup;

import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.FuelItem;
import com.ivomens.mcheli.aircraft.VehicleRecipeList;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.block.ChargerBlock;
import com.ivomens.mcheli.block.DraftingTableBlock;
import com.ivomens.mcheli.combat.weapon.MineDefuserItem;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.recipe.ContentRecipes;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentTranslations;
import com.ivomens.mcheli.equipment.gltd.GltdItem;
import com.ivomens.mcheli.equipment.launcher.LightWeaponAmmoItem;
import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.equipment.rangefinder.RangeFinderItem;
import com.ivomens.mcheli.equipment.throwable.ThrowableItem;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.equipment.wrench.WrenchItem;
import com.ivomens.mcheli.helicopter.HelicopterItem;
import com.ivomens.mcheli.plane.PlaneItem;
import com.ivomens.mcheli.registry.McheliBlocks;
import com.ivomens.mcheli.registry.McheliItems;
import com.ivomens.mcheli.registry.RegistryRef;
import com.ivomens.mcheli.ship.ShipItem;
import com.ivomens.mcheli.tank.TankItem;
import com.ivomens.mcheli.vehicle.uav.IUavStation;
import com.ivomens.mcheli.world.uavstation.UavPairingDeviceItem;
import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.vehicle.VehicleItem;
import com.ivomens.mcheli.world.cargo.ContainerItem;
import com.ivomens.mcheli.world.chain.ChainItem;
import com.ivomens.mcheli.world.gunner.SpawnGunnerItem;
import com.ivomens.mcheli.aircraft.parachute.ParachuteItem;
import com.ivomens.mcheli.world.uavstation.UavStationItem;
import com.ivomens.mcheli.world.uavstation.UavTabletItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates and registers MCHeli's blocks and items: the fixed ones and those the content files define,
 * and puts each into its creative tab. The loader calls these while the registry is open.
 */
public final class BlockAndItemRegistration {

    /** Spawn egg colors of the two gunner spawn items. */
    private static final int GUNNER_EGG_PRIMARY = 0xC0C0A0;
    private static final int GUNNER_VS_MONSTER_EGG_SECONDARY = 0xC00000;
    private static final int GUNNER_VS_PLAYER_EGG_SECONDARY = 0x00C000;

    private BlockAndItemRegistration() {
    }

    /** Creates and registers the blocks. Call while the block registry is open. */
    public static void registerBlocks() {
        register(new DraftingTableBlock(DraftingTableBlock.defaultProperties(false), false),
                McheliBlocks.DRAFTING_TABLE);
        register(new DraftingTableBlock(DraftingTableBlock.defaultProperties(true), true),
                McheliBlocks.LIT_DRAFTING_TABLE);
        register(new ChargerBlock(ChargerBlock.defaultProperties()), McheliBlocks.CHARGER);
    }

    /** Creates and registers the fixed and the content-defined items. Call while the item registry is open. */
    public static void registerItems() {
        registerFixedItems();
        registerThrowableItems();
        registerLightWeapons();
        registerAircraftItems();

        McheliCreativeTabs.ITEMS.addIconItem(registerBlockItem(McheliBlocks.DRAFTING_TABLE.get()));
        McheliCreativeTabs.ITEMS.addIconItem(registerBlockItem(McheliBlocks.CHARGER.get()));

        ContentRecipes.registerItemRecipes(contentRecipes());
    }

    private static void register(Block block, RegistryRef<Block> ref) {
        Registry.register(BuiltInRegistries.BLOCK, ref.id(), block);
    }

    private static BlockItem registerBlockItem(Block block) {
        BlockItem item = new BlockItem(block, new Item.Properties());
        Registry.register(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK.getKey(block), item);
        return item;
    }

    private static <T extends Item> T registerItem(T item, RegistryRef<Item> ref, @Nullable CreativeTab tab) {
        return registerItem(item, ref.id(), tab);
    }

    /** Registers the item and adds it to the creative tab (if any). */
    private static <T extends Item> T registerItem(T item, ResourceLocation id, @Nullable CreativeTab tab) {
        Registry.register(BuiltInRegistries.ITEM, id, item);
        if (tab != null) {
            tab.addIconItem(item);
        }
        return item;
    }

    private static void registerFixedItems() {
        CreativeTab items = McheliCreativeTabs.ITEMS;
        registerItem(newGunnerSpawner(0, GUNNER_VS_MONSTER_EGG_SECONDARY), McheliItems.SPAWN_GUNNER_VS_MONSTER, items);
        registerItem(newGunnerSpawner(1, GUNNER_VS_PLAYER_EGG_SECONDARY), McheliItems.SPAWN_GUNNER_VS_PLAYER, items);
        registerItem(new RangeFinderItem(new Item.Properties()), McheliItems.RANGEFINDER, items);
        registerItem(new WrenchItem(new Item.Properties()), McheliItems.WRENCH, items);
        registerItem(new FuelItem(new Item.Properties()), McheliItems.FUEL, items);
        registerItem(new GltdItem(new Item.Properties()), McheliItems.GLTD, items);
        registerItem(new ChainItem(), McheliItems.CHAIN, items);
        registerItem(new ParachuteItem(), McheliItems.PARACHUTE, items);
        registerItem(new ContainerItem(), McheliItems.CONTAINER, items);
        registerItem(new UavStationItem(new Item.Properties(), IUavStation.StationType.DEFAULT),
                McheliItems.UAV_STATION, items);
        registerItem(new UavStationItem(new Item.Properties(), IUavStation.StationType.SMALL),
                McheliItems.SMALL_UAV_STATION, items);
        registerItem(new UavPairingDeviceItem(new Item.Properties()), McheliItems.UAV_PAIRING_DEVICE, items);
        registerItem(new UavTabletItem(new Item.Properties()), McheliItems.UAV_TABLET, items);
        registerItem(new MineDefuserItem(), McheliItems.MINE_DEFUSER, items);
        registerItem(new InvisibleItem(new Item.Properties()), McheliItems.INVISIBLE, null);
    }

    private static SpawnGunnerItem newGunnerSpawner(int targetType, int secondaryColor) {
        SpawnGunnerItem item = new SpawnGunnerItem(new Item.Properties());
        item.setTargetType(targetType);
        item.setPrimaryColor(GUNNER_EGG_PRIMARY);
        item.setSecondaryColor(secondaryColor);
        return item;
    }

    private static void registerLightWeapons() {
        CreativeTab items = McheliCreativeTabs.ITEMS;
        LightWeaponAmmoItem stingerMissile = registerItem(new LightWeaponAmmoItem(), McheliItems.STINGER_MISSILE,
                items);
        LightWeaponAmmoItem javelinMissile = registerItem(new LightWeaponAmmoItem(), McheliItems.JAVELIN_MISSILE,
                items);
        registerItem(new LightWeaponItem(stingerMissile), McheliItems.STINGER, items);
        registerItem(new LightWeaponItem(javelinMissile), McheliItems.JAVELIN, items);
    }

    /** Registers the display name of a content-defined item in English and in every extra language. */
    private static void addNames(Item item, String displayName, @Nullable Map<String, String> namesByLang) {
        String key = item.getDescriptionId();
        ContentTranslations.put(ContentTranslations.DEFAULT_LANG, key, displayName);
        if (namesByLang != null) {
            namesByLang.forEach((lang, name) -> ContentTranslations.put(lang, key, name));
        }
    }

    private static void registerThrowableItems() {
        for (ThrowableDefinition throwable : Throwables.registry().values()) {
            ThrowableItem item = new ThrowableItem(throwable.itemId(), throwable.stackSize());
            ContentBindings.bindItem(Throwables.id(throwable), item);
            registerItem(item, Ids.of(throwable.name()), McheliCreativeTabs.ITEMS);
            ThrowableItem.registerDispenseBehavior(item);
            addNames(item, throwable.displayName().name(), throwable.displayName().translations());
        }
    }

    private static void registerAircraftItems() {
        registerAircraftItems(VehicleKind.HELICOPTER, HelicopterItem::new, McheliCreativeTabs.HELICOPTERS);
        registerAircraftItems(VehicleKind.PLANE, PlaneItem::new, McheliCreativeTabs.PLANES);
        registerAircraftItems(VehicleKind.SHIP, ShipItem::new, McheliCreativeTabs.SHIPS);
        registerAircraftItems(VehicleKind.TANK, TankItem::new, McheliCreativeTabs.TANKS);
        registerAircraftItems(VehicleKind.VEHICLE, VehicleItem::new, McheliCreativeTabs.VEHICLES);
    }

    private static void registerAircraftItems(VehicleKind kind, Function<Item.Properties, AircraftItem> newItem,
                                              CreativeTab tab) {
        for (VehicleSpec vehicle : VehicleTypes.registry(kind).values()) {
            VehicleFile file = vehicle.file();
            AircraftItem item = newItem.apply(new Item.Properties().durability(Math.max(1, file.maxHp())));
            ContentBindings.bindItem(vehicle.id(), item);
            // Supply-only aircraft that cannot be ridden go into the item tab.
            boolean supplyOnly = !file.canRide() && (file.ammoSupplyRange() > 0.0F || file.fuel().supplyRange() > 0.0F);
            registerItem(item, Ids.of(vehicle.name()), supplyOnly ? McheliCreativeTabs.ITEMS : tab);
            AircraftItem.registerDispenseBehavior(item);
            addNames(item, file.displayName().name(), file.displayName().translations());
        }
    }

    /** The vehicles and throwables whose files give recipes, in the order their recipes are generated. */
    private static List<ContentRecipes.RecipeSource> contentRecipes() {
        List<ContentRecipes.RecipeSource> sources = new ArrayList<>();
        for (VehicleKind kind : VehicleKind.values()) {
            for (VehicleSpec vehicle : VehicleTypes.registry(kind).values()) {
                VehicleFile.Recipe recipe = vehicle.file().recipe();
                sources.add(new ContentRecipes.RecipeSource(vehicle.name(), VehicleTypes.item(vehicle).orElse(null),
                        recipe.pattern(), recipe.shaped(), VehicleRecipeList.of(kind)));
            }
        }
        for (ThrowableDefinition throwable : Throwables.registry().values()) {
            sources.add(new ContentRecipes.RecipeSource(throwable.name(),
                    ContentBindings.item(Throwables.id(throwable)).orElse(null), throwable.recipe().pattern(),
                    throwable.recipe().shaped(), null));
        }
        return sources;
    }
}
