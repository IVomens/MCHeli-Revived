package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dispenser weapons use their item where their container lands. The item is bound once the game has
 * registered its items, at start-up; it used to be bound only when a weapon was read again in game, so in
 * normal play dispensers dropped containers that did nothing.
 */
public final class DispenserGameTests {

    /** A dispenser as an addon pack writes it: it lights a fire where it lands. */
    private static final String TEST_WEAPON = "gametest_fire_dispenser";
    private static final List<String> TEST_WEAPON_FILE = List.of(
            "DisplayName: Game Test Fire Dispenser",
            "Type: dispenser",
            "DispenseItem:",
            "  Location: minecraft:flint_and_steel");

    private DispenserGameTests() {
    }

    public static List<TestFunction> dispensers() {
        if (!StressTests.SCENARIO.isEmpty()) {
            return List.of();
        }
        return List.of(
                new TestFunction("mcheli_dispenser", "loaded_dispensers_have_their_item_after_start", "mcheli:empty",
                        20, 0, true, DispenserGameTests::loadedDispensersHaveTheirItem),
                new TestFunction("mcheli_dispenser", "dispenser_uses_its_item_where_it_lands", "mcheli:empty",
                        200, 0, true, DispenserGameTests::dispenserUsesItsItemWhereItLands));
    }

    /** Every dispenser the game started with has the item its file names, without a reload. */
    private static void loadedDispensersHaveTheirItem(GameTestHelper h) {
        for (WeaponSpec weapon : Weapons.registry().values()) {
            Optional<Item> named = namedItem(weapon);
            if (named.isPresent() && !named.equals(ContentBindings.dispensedItem(weapon.id()))) {
                h.fail(weapon.name() + " dispenses " + ContentBindings.dispensedItem(weapon.id()) + ", its file names "
                        + named.get());
                return;
            }
        }
        h.succeed();
    }

    /**
     * A dispenser read like an addon pack's file is bound by the start-up step that runs once the items exist,
     * and its container lights a fire on the stone it lands on.
     */
    private static void dispenserUsesItsItemWhereItLands(GameTestHelper h) {
        ContentRegistry<WeaponDefinition, WeaponSpec> registry = Weapons.registry();
        List<LoadedContent<WeaponDefinition, WeaponSpec>> startedWith = List.copyOf(registry.loaded());
        LoadedContent<WeaponDefinition, WeaponSpec> testWeapon;
        try {
            testWeapon = readAsAddon(registry);
        } catch (Exception e) {
            h.fail("could not read the test dispenser: " + e);
            return;
        }
        List<LoadedContent<WeaponDefinition, WeaponSpec>> withTestWeapon = new ArrayList<>(startedWith);
        withTestWeapon.add(testWeapon);
        registry.fill(withTestWeapon);
        Weapons.onItemsRegistered();

        if (!ContentBindings.dispensedItem(testWeapon.value().id()).equals(Optional.of(Items.FLINT_AND_STEEL))) {
            registry.fill(startedWith);
            h.fail("the start-up binding left the dispenser without its item");
            return;
        }
        BlockPos floor = new BlockPos(2, 1, 2);
        h.setBlock(floor, Blocks.STONE);
        Vec3 dropFrom = h.absoluteVec(Vec3.atCenterOf(floor).add(0.0, 3.0, 0.0));
        // a container whose shooter is far away is dropped, so the shooter stands next to the floor
        ServerPlayer shooter = h.makeMockServerPlayerInLevel();
        shooter.moveTo(dropFrom.x + 2.0, dropFrom.y - 2.5, dropFrom.z);
        ProjectileEntity container = ProjectileEntity.create(ProjectileKind.DISPENSED_ITEM, h.getLevel(), dropFrom,
                new Vec3(0.0, -1.0, 0.0), 0.0F, 90.0F, 0.5);
        container.setWeapon(TEST_WEAPON);
        container.setShooter(shooter, shooter);
        h.getLevel().addFreshEntity(container);

        h.onEachTick(() -> {
            if (h.getBlockState(floor.above()).is(Blocks.FIRE)) {
                registry.fill(startedWith);
                shooter.discard();
                h.succeed();
            }
        });
        h.runAfterDelay(180, () -> {
            registry.fill(startedWith);
            shooter.discard();
            h.fail("no fire where the container landed; the container was " + container.getRemovalReason()
                    + " at " + container.position());
        });
    }

    private static LoadedContent<WeaponDefinition, WeaponSpec> readAsAddon(
            ContentRegistry<WeaponDefinition, WeaponSpec> registry) throws Exception {
        AddonResourceLocation location = new AddonResourceLocation(Ids.of(TEST_WEAPON), "gametest");
        ContentEntry<?> entry = ContentFormat.of(ContentType.WEAPON).read(ContentType.WEAPON, location,
                "assets/mcheli/weapons/" + TEST_WEAPON + ".yml", ContentFormat.YAML, TEST_WEAPON_FILE, false);
        return registry.bind(entry);
    }

    private static Optional<Item> namedItem(WeaponSpec weapon) {
        String location = weapon.definition().dispenseItem().location();
        ResourceLocation id = location != null ? ResourceLocation.tryParse(location) : null;
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? Optional.of(BuiltInRegistries.ITEM.get(id))
                : Optional.empty();
    }
}
