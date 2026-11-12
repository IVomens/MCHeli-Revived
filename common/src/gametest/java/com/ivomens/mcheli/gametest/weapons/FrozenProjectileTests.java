package com.ivomens.mcheli.gametest.weapons;

import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.ProjectileKind;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.content.source.ContentFormat;
import com.ivomens.mcheli.gametest.snapshot.ForcedArea;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Projectiles outside their flight ({@code ProjectileLifetime}): a round that flew into a chunk that is
 * loaded but ticks no entities waits there, and is gone once it waited longer than
 * {@code FrozenProjectileLifetime}; one that waited less flies on when the chunk ticks again; a mine waits
 * as long as it takes. Only mines are written to saves, and a round an older version saved is dropped
 * when it is loaded.
 * <p>
 * The test runs far from the other tests' areas, in a chunk it force-loads ({@code A}, which ticks
 * entities) and the chunk east of it ({@code B}, loaded by A's ticket but ticking no entities), and needs a
 * mine weapon, which the bundled content has none of: one is read like an addon's file for the test's
 * length. It is a batch of its own, so no other test runs while the weapon is registered.
 */
public final class FrozenProjectileTests {

    private static final String MINE_WEAPON = "gametest_frozen_mine";
    private static final List<String> MINE_WEAPON_FILE = List.of(
            "DisplayName: Game Test Mine",
            "Type: bomb",
            "Mine:",
            "  Fuze: ProximityPlayer");
    private static final String ROUND_WEAPON = "m134";
    /** Chunk A, far from the test grid and from the weapon range of the snapshots. */
    private static final ChunkPos TICKING = new ChunkPos(-400, -400);
    private static final double HEIGHT = 200.0;
    /** When the first round flies into B, and how long after its lifetime B starts ticking. */
    private static final int FIRST_SHOT = 20;
    private static final int RESUME_AFTER_LIFETIME = 30;
    /** The second round has waited this much less than the lifetime when B starts ticking. */
    private static final int SHORT_WAIT_MARGIN = 200;

    private FrozenProjectileTests() {
    }

    public static List<TestFunction> lifetime() {
        int lifetimeTicks = McheliConfig.network().frozenProjectileLifetime() * 20;
        int timeout = FIRST_SHOT + lifetimeTicks + RESUME_AFTER_LIFETIME + 200;
        return List.of(
                new TestFunction("mcheli_projectile_lifetime_saves", "only_mines_are_saved", "mcheli:empty", 40, 0,
                        true, FrozenProjectileTests::onlyMinesAreSaved),
                new TestFunction("mcheli_projectile_lifetime_frozen", "frozen_round_expires_mine_stays",
                        "mcheli:empty", timeout, 0, true, FrozenProjectileTests::frozenRoundExpires));
    }

    /**
     * What a chunk save writes ({@code Entity#save}, as the entity storage calls it): nothing for a round, the
     * mine with its state; the mine loads back, while a round saved by an older version goes on its first tick.
     */
    private static void onlyMinesAreSaved(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ContentRegistry<WeaponDefinition, WeaponSpec> registry = Weapons.registry();
        List<LoadedContent<WeaponDefinition, WeaponSpec>> startedWith = List.copyOf(registry.loaded());
        Vec3 at = h.absoluteVec(new Vec3(2.5, 6.0, 2.5));
        ProjectileEntity round = projectile(level, ProjectileKind.BULLET, ROUND_WEAPON, at, Vec3.ZERO);
        ProjectileEntity mine;
        Entity loadedMine;
        try {
            registerMine(registry, startedWith);
            mine = projectile(level, ProjectileKind.BOMB, MINE_WEAPON, at, Vec3.ZERO);
            CompoundTag mineTag = new CompoundTag();
            h.assertTrue(mine.save(mineTag), "the mine was not written to the save");
            h.assertTrue(mineTag.getBoolean("MchMine"), "the saved mine lost its mine state: " + mineTag);
            loadedMine = EntityType.create(mineTag, level).orElse(null);
            h.assertTrue(loadedMine instanceof ProjectileEntity p && MINE_WEAPON.equals(p.weaponName()),
                    "the mine did not load back: " + loadedMine);
        } finally {
            registry.fill(startedWith);
        }
        h.assertFalse(round.save(new CompoundTag()), "a round was written to the save");

        // a round an older version saved
        CompoundTag old = new CompoundTag();
        old.putString("id", EntityType.getKey(round.getType()).toString());
        round.saveWithoutId(old);
        Entity loadedRound = EntityType.create(old, level).orElseThrow();
        level.addFreshEntity(loadedRound);
        h.runAfterDelay(2, () -> {
            h.assertTrue(loadedRound.isRemoved(), "a round loaded from an old save is still there");
            h.succeed();
        });
    }

    /**
     * A round flies into B and stops there. It is gone once it waited longer than the lifetime, even before
     * B ticks again; a round that flies into B later, and has waited less when B starts ticking, flies on.
     * A mine dropped into B is still there at the end.
     */
    private static void frozenRoundExpires(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int lifetimeTicks = McheliConfig.network().frozenProjectileLifetime() * 20;
        if (lifetimeTicks <= 0) {
            h.succeed();
            return;
        }
        ChunkPos east = new ChunkPos(TICKING.x + 1, TICKING.z);
        ForcedArea ticking = new ForcedArea(level, TICKING, TICKING, (int) HEIGHT);
        ForcedArea frozen = new ForcedArea(level, east, east, (int) HEIGHT);
        ticking.force(true);
        ticking.awaitReady();
        BlockPos inB = east.getMiddleBlockPosition((int) HEIGHT);
        h.assertTrue(level.hasChunkAt(inB) && !level.isPositionEntityTicking(inB),
                "the chunk east of the forced one is not loaded without ticking entities");

        ContentRegistry<WeaponDefinition, WeaponSpec> registry = Weapons.registry();
        List<LoadedContent<WeaponDefinition, WeaponSpec>> startedWith = List.copyOf(registry.loaded());
        ProjectileEntity mine;
        try {
            registerMine(registry, startedWith);
            mine = projectile(level, ProjectileKind.BOMB, MINE_WEAPON, Vec3.atCenterOf(inB), Vec3.ZERO);
        } finally {
            registry.fill(startedWith);
        }
        // the shooter stays in A: a projectile without a shooter is dropped when it flies
        Entity shooter = EntityType.MARKER.create(level);
        Vec3 border = new Vec3(TICKING.getMaxBlockX() + 0.5, HEIGHT, TICKING.getMiddleBlockZ() + 0.5);
        shooter.moveTo(border.x - 8.0, HEIGHT, border.z);
        level.addFreshEntity(shooter);
        mine.setShooter(shooter, shooter);
        level.addFreshEntity(mine);

        List<Entity> spawned = new ArrayList<>(List.of(shooter, mine));
        ProjectileEntity[] rounds = new ProjectileEntity[2];
        Vec3[] frozenAt = new Vec3[2];
        int resume = FIRST_SHOT + lifetimeTicks + RESUME_AFTER_LIFETIME;
        int secondShot = resume - (lifetimeTicks - SHORT_WAIT_MARGIN);
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            int t = tick[0];
            if (t == FIRST_SHOT || t == secondShot) {
                int i = t == FIRST_SHOT ? 0 : 1;
                rounds[i] = projectile(level, ProjectileKind.BULLET, ROUND_WEAPON, border.add(0, i, 0),
                        new Vec3(1, 0, 0));
                rounds[i].setShooter(shooter, shooter);
                level.addFreshEntity(rounds[i]);
                spawned.add(rounds[i]);
            } else if (t == FIRST_SHOT + 10 || t == secondShot + 10) {
                int i = t == FIRST_SHOT + 10 ? 0 : 1;
                h.assertFalse(rounds[i].isRemoved(), "round " + i + " was " + rounds[i].getRemovalReason()
                        + " before it waited long");
                h.assertTrue(new ChunkPos(rounds[i].blockPosition()).equals(east),
                        "round " + i + " did not stop in the chunk that ticks no entities: " + rounds[i].position());
                frozenAt[i] = rounds[i].position();
            } else if (t == resume) {
                h.assertTrue(rounds[0].isRemoved(), "the first round is still there after waiting "
                        + (t - FIRST_SHOT) + " ticks");
                h.assertFalse(rounds[1].isRemoved(), "the second round went before its time");
                frozen.force(true);
                frozen.awaitReady();
            } else if (t == resume + 10) {
                end(h, spawned, ticking, frozen, () -> {
                    h.assertFalse(mine.isRemoved(), "the mine was " + mine.getRemovalReason());
                    h.assertTrue(rounds[1].isRemoved() || !rounds[1].position().equals(frozenAt[1]),
                            "the second round did not fly on when its chunk ticked again");
                });
            }
        });
    }

    private static void end(GameTestHelper h, List<Entity> spawned, ForcedArea ticking, ForcedArea frozen,
                            Runnable checks) {
        try {
            checks.run();
        } finally {
            spawned.forEach(Entity::discard);
            frozen.force(false);
            ticking.force(false);
        }
        h.succeed();
    }

    private static ProjectileEntity projectile(ServerLevel level, ProjectileKind kind, String weapon, Vec3 at,
                                               Vec3 direction) {
        Vec3 dir = direction.lengthSqr() > 0 ? direction : new Vec3(0, -1, 0);
        ProjectileEntity projectile = ProjectileEntity.create(kind, level, at, dir, 0.0F, 0.0F,
                direction.lengthSqr() > 0 ? 1.0 : 0.01);
        projectile.setWeapon(weapon);
        return projectile;
    }

    private static void registerMine(ContentRegistry<WeaponDefinition, WeaponSpec> registry,
                                     List<LoadedContent<WeaponDefinition, WeaponSpec>> startedWith) {
        try {
            AddonResourceLocation location = new AddonResourceLocation(Ids.of(MINE_WEAPON), "gametest");
            ContentEntry<?> entry = ContentFormat.of(ContentType.WEAPON).read(ContentType.WEAPON, location,
                    "assets/mcheli/weapons/" + MINE_WEAPON + ".yml", ContentFormat.YAML, MINE_WEAPON_FILE, false);
            List<LoadedContent<WeaponDefinition, WeaponSpec>> withMine = new ArrayList<>(startedWith);
            withMine.add(registry.bind(entry));
            registry.fill(withMine);
        } catch (Exception e) {
            throw new IllegalStateException("could not read the test mine", e);
        }
    }
}
