package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated-server smoke tests: every vehicle definition is spawned from its item, boarded by a
 * player, ticked, and has every weapon fired. Any exception fails the test with the vehicle name.
 */
public class VehicleGameTests {

    /** The fuel fluid each loader registers. */
    private static final ResourceLocation FUEL = Ids.of("mch_fuel");

    public static List<TestFunction> vehicles() {
        List<TestFunction> tests = new ArrayList<>();
        if (!StressTests.SCENARIO.isEmpty()) return tests;
        add(tests, "heli", VehicleTypes.registry(VehicleKind.HELICOPTER));
        add(tests, "plane", VehicleTypes.registry(VehicleKind.PLANE));
        add(tests, "tank", VehicleTypes.registry(VehicleKind.TANK));
        add(tests, "vehicle", VehicleTypes.registry(VehicleKind.VEHICLE));
        add(tests, "ship", VehicleTypes.registry(VehicleKind.SHIP));
        tests.add(new TestFunction("mcheli_rest", "parked_vehicle_rests_and_wakes", "mcheli:empty", 400, 0, true,
                VehicleGameTests::parkedVehicleRestsAndWakes));
        tests.add(new TestFunction("mcheli_content", "drafting_recipes_count_ingredients", "mcheli:empty", 20, 0, true,
                VehicleGameTests::draftingRecipesCountIngredients));
        tests.add(new TestFunction("mcheli_content", "throwable_times_are_ticks", "mcheli:empty", 20, 0, true,
                VehicleGameTests::throwableTimesAreTicks));
        tests.add(new TestFunction("mcheli_content", "aircraft_save_round_trip", "mcheli:empty", 20, 0, true,
                VehicleGameTests::aircraftSaveRoundTrip));
        return tests;
    }

    private static <T extends VehicleSpec> void add(List<TestFunction> tests, String kind,
                                                    ContentRegistry<?, T> registry) {
        for (T info : registry.values()) {
            String name = (kind + "_" + info.name()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            tests.add(new TestFunction("mcheli_" + kind, name, "mcheli:empty", 2400, 0, true, h -> run(h, info)));
        }
    }

    /** A parked vehicle stops ticking, and wakes when damaged, when boarded, and when its ground is removed. */
    private static void parkedVehicleRestsAndWakes(GameTestHelper h) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        var info = VehicleTypes.registry(VehicleKind.HELICOPTER).get("ah-64").orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("ah-64 missing");
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(16, 1.2, 16));
        AircraftEntity ac = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item));
        h.getLevel().addFreshEntity(ac);
        h.runAfterDelay(80, () -> {
            if (!ac.rest().isResting()) h.fail("parked vehicle did not rest: " + ac.rest().status());
            ac.hurt(h.getLevel().damageSources().generic(), 0.5F);
            if (ac.rest().isResting()) h.fail("damage did not wake it");
        });
        h.runAfterDelay(200, () -> {
            if (!ac.rest().isResting()) h.fail("did not rest again after repair: " + ac.rest().status());
            ServerPlayer player = h.makeMockServerPlayerInLevel();
            player.moveTo(pos.x + 2, pos.y, pos.z);
            ac.boarding().interact(player, InteractionHand.MAIN_HAND);
            if (ac.rest().isResting()) h.fail("interacting did not wake it");
            player.discard();
        });
        h.runAfterDelay(300, () -> {
            double y = ac.getY();
            for (int x = 8; x < 24; x++) {
                for (int z = 8; z < 24; z++) {
                    h.setBlock(new BlockPos(x, 0, z), Blocks.AIR);
                }
            }
            h.runAfterDelay(60, () -> {
                if (ac.getY() >= y - 0.5) h.fail("vehicle did not fall after its ground was removed");
                else h.succeed();
            });
        });
    }

    /**
     * Fuel, trunk and fuel/parachute slots survive a save and load, in the NBT layout older versions
     * wrote, and stay reachable for pipes through the item and fluid handler capabilities.
     */
    private static void aircraftSaveRoundTrip(GameTestHelper h) {
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).values().stream()
                .filter(i -> i.file().features().inventorySize() > 1 && i.file().fuel().maxFuel() > 200
                        && !i.file().fuel().electric())
                .findFirst().orElse(null);
        if (info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("no helicopter with a trunk and a fuel tank");
            return;
        }
        ServerLevel level = h.getLevel();
        Vec3 pos = h.absoluteVec(new Vec3(4, 2, 4));
        AircraftEntity ac = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        ac.fuel().setFuelType("mch_fuel");
        ac.fuel().setAmount(150);
        ac.getInventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 17));
        ac.getGuiInventory().getItemHandler().setStackInSlot(AircraftInventory.SLOT_FUEL_OUT, new ItemStack(Items.BUCKET, 3));
        ac.getGuiInventory().getItemHandler().setStackInSlot(AircraftInventory.SLOT_PARACHUTE0, new ItemStack(Items.FEATHER));

        CompoundTag tag = new CompoundTag();
        ac.saveWithoutId(tag);
        ac.discard();
        expect(h, tag.getInt("AcFuel") == 150 && "mch_fuel".equals(tag.getString("AcFuelType")), "fuel not saved: " + tag);
        CompoundTag trunk = tag.getCompound("items");
        expect(h, trunk.getInt("Size") == info.file().features().inventorySize(), "trunk size not saved: " + trunk);
        ListTag trunkItems = trunk.getList("Items", Tag.TAG_COMPOUND);
        expect(h, trunkItems.size() == 1 && trunkItems.getCompound(0).getInt("Slot") == 1
                && trunkItems.getCompound(0).getInt("count") == 17, "trunk not saved as before: " + trunk);
        CompoundTag slots = tag.getCompound("ItemsAC");
        expect(h, slots.getInt("Size") == AircraftInventory.SIZE
                && slots.getList("Items", Tag.TAG_COMPOUND).size() == 2, "fuel slots not saved as before: " + slots);

        if (!(ac.getType().create(level) instanceof AircraftEntity copy)) {
            h.fail("could not create a copy of " + info.name());
            return;
        }
        copy.load(tag);
        expect(h, copy.fuel().amount() == 150 && "mch_fuel".equals(copy.fuel().fuelType()), "fuel not loaded: " + copy.fuel().amount());
        expect(h, copy.getInventory().getSlots() == info.file().features().inventorySize(), "trunk size changed");
        ItemStack ingots = copy.getInventory().getStackInSlot(1);
        expect(h, ingots.is(Items.IRON_INGOT) && ingots.getCount() == 17, "trunk not loaded: " + ingots);
        ItemStack buckets = copy.getGuiInventory().getFuelSlotItemStack(AircraftInventory.SLOT_FUEL_OUT);
        expect(h, buckets.is(Items.BUCKET) && buckets.getCount() == 3, "fuel slot not loaded: " + buckets);
        expect(h, copy.getGuiInventory().getFuelSlotItemStack(AircraftInventory.SLOT_PARACHUTE0).is(Items.FEATHER),
                "parachute slot not loaded");

        checkExposedStorage(h, copy, info);
        copy.discard();
        h.succeed();
    }

    /** The trunk and the fuel tank as other mods reach them, where the loader has an entity storage API. */
    private static void checkExposedStorage(GameTestHelper h, AircraftEntity aircraft, VehicleSpec info) {
        ExposedStorage storage = ExposedStorage.get();
        ExposedStorage.Slots items = storage.items(aircraft);
        if (items != null) {
            expect(h, items.size() == info.file().features().inventorySize() && items.get(1).getCount() == 17,
                    "trunk not exposed as an item storage");
            ItemStack rest = items.insert(1, new ItemStack(Items.IRON_INGOT, 60));
            expect(h, rest.getCount() == 13 && aircraft.getInventory().getStackInSlot(1).getCount() == 64,
                    "insert into the trunk failed");
        }
        ExposedStorage.Tank tank = storage.fluids(aircraft);
        if (tank != null) {
            expect(h, tank.amount() == 150, "fuel tank not exposed as a fluid storage");
            int filled = tank.fill(BuiltInRegistries.FLUID.get(FUEL), 100, false);
            expect(h, filled == 100 && aircraft.fuel().amount() == 250, "filling through the fluid storage failed: " + filled);
            expect(h, tank.fill(Fluids.WATER, 100, true) == 0, "water accepted as fuel");
        }
    }

    private static void expect(GameTestHelper h, boolean condition, String message) {
        if (!condition) {
            h.fail(message);
        }
    }

    /** A recipe that needs two of an item must not be craftable with one, and takes exactly two. */
    private static void draftingRecipesCountIngredients(GameTestHelper h) {
        var recipe = new net.minecraft.world.item.crafting.ShapelessRecipe("", net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK), net.minecraft.core.NonNullList.of(
                net.minecraft.world.item.crafting.Ingredient.EMPTY,
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT),
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT)));
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
        if (com.ivomens.mcheli.content.recipe.InventoryIngredients.canCraft(player, recipe)) {
            h.fail("craftable with one ingot");
        } else if (com.ivomens.mcheli.content.recipe.InventoryIngredients.consume(player, recipe)
                || player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) != 1) {
            h.fail("a failed craft took ingredients");
        } else {
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
            if (!com.ivomens.mcheli.content.recipe.InventoryIngredients.consume(player, recipe)
                    || player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) != 1) {
                h.fail("expected two of three ingots to be used");
            } else {
                h.succeed();
            }
        }
        player.discard();
    }

    /** YAML definitions store ticks; they must not be converted from seconds a second time. */
    private static void throwableTimesAreTicks(GameTestHelper h) {
        var info = Throwables.registry().get("m18_red").orElse(null);
        if (info == null) {
            h.fail("m18_red is not loaded");
        } else if (info.timeFuse() != 40 || info.aliveTime() != 600) {
            h.fail("m18_red fuse/alive = " + info.timeFuse() + "/" + info.aliveTime() + ", expected 40/600");
        } else {
            h.succeed();
        }
    }

    private static void run(GameTestHelper h, VehicleSpec info) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        Item item = VehicleTypes.item(info).orElse(null);
        if (!(item instanceof AircraftItem acItem)) {
            h.fail("no aircraft item for " + info.name());
            return;
        }
        Vec3 pos = h.absoluteVec(new Vec3(16, 2, 16));
        AircraftEntity ac = acItem.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        if (ac == null) {
            h.fail("createAircraft returned null for " + info.name());
            return;
        }
        level.addFreshEntity(ac);

        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.moveTo(pos.x + 2, pos.y, pos.z);
        // Board like a player: the first click may only open the canopy, so keep clicking.
        boolean rideable = info.file().canRide() && !ac.isUAV();
        for (int t = 5; t <= 105; t += 10) {
            h.runAfterDelay(t, () -> {
                if (ac.seats().seatIdOf(player) < 0 && !ac.isRemoved()) {
                    ac.boarding().interact(player, InteractionHand.MAIN_HAND);
                    // planes would take off into neighbouring test areas, so they only idle
                    ac.engine().setThrottle(ac instanceof com.ivomens.mcheli.plane.PlaneEntity ? 0.0 : 1.0);
                }
            });
        }
        // Cycle through every weapon like a player would: select it, then keep pulling the trigger
        // until it fires (switch cooldown / initial reload) or 200 ticks pass, then move on.
        List<String> misses = new ArrayList<>();
        int[] state = {0, 0, 0}; // weapon index, ticks on this weapon, weapons fired
        h.onEachTick(() -> {
            if (ac.isRemoved() || !ac.isPilot(player) && ac.seats().seatIdOf(player) < 0) return;
            int n = ac.weapons().count();
            if (state[0] >= n) return;
            if (state[1] == 0) ac.weapons().select(player, state[0]);
            state[1]++;
            if (state[1] > 2 && ac.weapons().fire(player)) {
                state[2]++;
                state[0]++;
                state[1] = 0;
            } else if (state[1] > 200) {
                misses.add(ac.weapons().current(player) == null ? "#" + state[0]
                        : ac.weapons().current(player).spec().name());
                state[0]++;
                state[1] = 0;
            }
        });
        h.succeedWhen(() -> {
            if (ac.isRemoved() && !ac.isDestroyed() && ac.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(info.name() + " vanished: "
                        + ac.getRemovalReason() + " at " + ac.position());
            }
            if (rideable && ac.seats().seatIdOf(player) < 0) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("could not board " + info.name());
            }
            if (rideable && state[0] < ac.weapons().count()) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("still cycling weapons");
            }
            com.ivomens.mcheli.util.ModLogger.get().info("[gametest] {} weapons={} fired={} misses={}",
                    info.name(), ac.weapons().count(), state[2], misses);
            player.stopRiding();
            ac.discard();
        });
    }
}
