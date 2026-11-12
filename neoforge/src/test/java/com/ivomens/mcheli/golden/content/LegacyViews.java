package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.combat.weapon.Cartridge;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.content.def.weapon.AmmoItem;
import com.ivomens.mcheli.content.def.weapon.NtmPayload;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.equipment.throwable.Throwables;
import com.ivomens.mcheli.hud.Hud;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The content dumps as the info adapters wrote them before the content rewrite: the old class and field
 * names, with today's values. Weapons, throwables and HUDs are here; vehicles have {@link LegacyVehicleView}.
 * The canonical dump goldens are compared through these views, so a value that changed is a one-line diff.
 */
final class LegacyViews {

    private LegacyViews() {
    }

    /** The legacy view of a loaded file, by its content type. */
    static LegacyObject of(LoadedContent<?, ?> loaded) {
        return switch (loaded.value()) {
            case WeaponSpec weapon -> weapon(loaded.entry(), weapon);
            case ThrowableDefinition throwable -> throwable(loaded.entry(), throwable);
            case Hud hud -> hud(cast(loaded.entry()), hud);
            case VehicleSpec vehicle -> LegacyVehicleView.of(loaded.entry(), vehicle);
            default -> throw new IllegalArgumentException("no legacy view of " + loaded.value().getClass());
        };
    }

    /** The simple name of the class the file loaded as before the rewrite. */
    static String legacyClassName(LoadedContent<?, ?> loaded) {
        String name = of(loaded).className();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    @SuppressWarnings("unchecked")
    private static <D extends com.ivomens.mcheli.core.content.schema.ContentDefinition> ContentEntry<D> cast(
            ContentEntry<?> entry) {
        return (ContentEntry<D>) entry;
    }

    /** The fields every content file had: where it came from and who made it. */
    static LegacyObject.Builder file(String className, ContentEntry<?> entry) {
        return LegacyObject.of(className)
                .put("filePath", entry.filePath())
                .put("location", entry.location())
                .put("author", entry.author())
                .put("parserIdentifier", entry.format());
    }

    /** A reference to other content, as {@code <Hud heli>}. */
    static LegacyObject.Text hudReference(Hud hud) {
        return hud == null ? null : new LegacyObject.Text("<Hud " + hud.name() + ">");
    }

    private static LegacyObject hud(ContentEntry<HudProgram> entry, Hud hud) {
        return file("com.ivomens.mcheli.hud.Hud", entry)
                .put("name", hud.name())
                .put("fileName", hud.fileName())
                .put("ignoreAutoScale", hud.ignoreAutoScale())
                .rawLines((prefix, lines) -> LegacyHudView.writeFields(cast(entry), prefix, lines))
                .build();
    }

    private static LegacyObject throwable(ContentEntry<?> entry, ThrowableDefinition t) {
        return file("com.ivomens.mcheli.equipment.throwable.ThrowableInfo", entry)
                .put("name", t.name())
                .put("stackSize", t.stackSize())
                .put("displayName", t.displayName().name())
                .put("displayNameLang", new HashMap<>(t.displayName().translations()))
                .put("itemID", t.itemId())
                // the info was dumped as read, before items were registered
                .put("item", null)
                .put("recipeString", t.recipe().pattern())
                .put("recipe", List.of())
                .put("isShapedRecipe", t.recipe().shaped())
                .put("power", t.power())
                .put("acceleration", t.acceleration())
                .put("accelerationInWater", t.accelerationInWater())
                .put("dispenseAcceleration", t.dispenseAcceleration())
                .put("explosion", t.explosion())
                .put("explosionBlock", -1)
                .put("explosionRadius", 0.0F)
                .put("explosionDamageRadius", 0.0F)
                .put("isNewExplosionBreak", false)
                .put("delayFuse", t.delayFuse())
                .put("bound", t.bound())
                .put("timeFuse", t.timeFuse())
                .put("flaming", t.flaming())
                .put("soundVolume", t.sound().volume())
                .put("soundPitch", t.sound().pitch())
                .put("proximityFuseDist", t.proximityFuseDist())
                .put("accuracy", t.accuracy())
                .put("aliveTime", t.aliveTime())
                .put("bomblet", t.bomblet())
                .put("bombletDiff", t.bombletSpread())
                .put("smokeSize", t.smoke().size())
                .put("smokeNum", t.smoke().count())
                .put("smokeVelocityVertical", t.smoke().velocity().vertical())
                .put("smokeVelocityHorizontal", t.smoke().velocity().horizontal())
                .put("gravity", t.gravity())
                .put("gravityInWater", t.gravityInWater())
                .put("disableSmoke", t.smoke().disabled())
                .put("smokeColor", Throwables.smokeColor(t))
                .build();
    }

    private static LegacyObject weapon(ContentEntry<?> entry, WeaponSpec w) {
        WeaponDefinition d = w.definition();
        LegacyObject.Builder view = file("com.ivomens.mcheli.combat.weapon.WeaponInfo", entry)
                .put("name", w.name())
                .put("displayName", d.displayName())
                .put("type", d.type())
                .put("group", d.group())
                .put("power", d.baseDamage())
                .put("delay", w.delay())
                .put("target", d.target())
                .put("modeNum", d.modes())
                .put("fixMode", w.fixMode())
                .put("ridableOnly", d.ridableOnly())
                .put("accuracy", d.accuracy())
                .put("destruct", d.selfDestruct())
                .put("enableChunkLoader", d.canLoadChunks())
                .put("length", w.length())
                .put("radius", w.radius())
                .put("angle", w.angle())
                .put("markTime", d.markTime())
                .put("damageFactor", w.damageFactor() == null ? null
                        : LegacyObject.of("com.ivomens.mcheli.content.def.DamageFactor")
                                .put("map", w.damageFactor().factors()).build());
        ntm(view, w);
        ballistics(view, d.ballistics());
        sound(view, d.sound());
        recoil(view, d.recoil());
        explosion(view, w, d.explosion());
        camera(view, w, d.camera());
        missile(view, d.missile());
        ammo(view, w, d.ammo());
        render(view, w, d.render());
        submunition(view, w, d.submunition());
        return view
                .put("potionEffect", w.potionEffects())
                .put("bulletDecay", w.bulletDecay())
                .put("enableBulletDecay", w.enableBulletDecay())
                .put("dispenseItemLoc", d.dispenseItem().location())
                // The bound item depends on whether items were registered earlier in the same JVM, so the
                // dump keeps the old "as read" value, like the vehicle and throwable items.
                .put("dispenseItem", null)
                .put("dispenseDamage", d.dispenseItem().meta())
                .put("dispenseRange", d.dispenseItem().range())
                .put("buckshotCount", d.buckshot().count())
                .put("buckshotPayload", new LegacyObject.Text("BuckshotPayload." + w.buckshotPayload().name()))
                .put("mineFuze", d.mine().fuze())
                .put("mineRange", w.mineRange())
                .put("mineArmDelay", w.mineArmDelay())
                .build();
    }

    private static void ntm(LegacyObject.Builder view, WeaponSpec w) {
        NtmPayload ntm = w.definition().ntm();
        view.put("useHBM", w.useHBM())
                .put("payloadNTM", new LegacyObject.Text("Payload." + w.payloadNTM().name()))
                .put("effectOnly", w.effectOnly())
                .put("vntSettingContainer", ntm == null ? null : ntm.vnt())
                .put("chemicalContainer", null)
                .put("mistContainer", ntm == null ? null : ntm.mist())
                .put("mukeContainer", ntm == null ? null : ntm.miniNuke())
                .put("ntSettingContainer", ntm == null ? null : ntm.nt())
                .put("fireContainer", ntm == null ? null : ntm.fire());
    }

    private static void ballistics(LegacyObject.Builder view, WeaponDefinition.Ballistics b) {
        view.put("acceleration", b.acceleration())
                .put("accelerationInWater", b.accelerationInWater())
                .put("gravity", b.gravity())
                .put("gravityInWater", b.gravityInWater())
                .put("velocityInWater", b.velocityInWater())
                .put("speedFactor", b.speedFactor())
                .put("speedFactorStartTick", b.speedFactorStartTick())
                .put("speedFactorEndTick", b.speedFactorEndTick())
                .put("speedDependsAircraft", b.speedDependsAircraft())
                .put("piercing", b.piercing())
                .put("flakParticlesDiff", b.flakSpread());
    }

    private static void sound(LegacyObject.Builder view, WeaponDefinition.Sound s) {
        view.put("fireSound", s.locations().fire())
                .put("hitSound", s.locations().hit())
                .put("railgunSound", s.locations().railgun())
                .put("soundDelay", s.delay())
                .put("soundVolume", s.volume())
                .put("soundPitch", s.pitch())
                .put("soundPitchRandom", s.pitchRandom())
                .put("hitSoundRange", s.hitSoundRange());
    }

    private static void recoil(LegacyObject.Builder view, WeaponDefinition.Recoil r) {
        view.put("recoil", r.power())
                .put("recoilPitch", r.pitch())
                .put("recoilYaw", r.yaw())
                .put("recoilPitchRange", r.pitchRange())
                .put("recoilYawRange", r.yawRange())
                .put("recoilRecoverFactor", r.recoverFactor())
                .put("recoilBufCount", r.bufferCount())
                .put("recoilBufCountSpeed", r.bufferSpeed());
    }

    private static void explosion(LegacyObject.Builder view, WeaponSpec w, WeaponDefinition.Explosion x) {
        view.put("explosion", x.power())
                .put("explosionBlock", w.explosionBlock())
                .put("explosionRadius", w.explosionRadius())
                .put("explosionDamageRadius", x.damageRadius())
                .put("isFAE", w.isFAE())
                .put("explosionInWater", w.explosionInWater())
                .put("explosionAltitude", x.altitude())
                .put("timeFuse", x.fuseTime())
                .put("delayFuse", x.fuseDelay())
                .put("bound", w.bound())
                .put("flaming", x.flaming())
                .put("canAirburst", x.canAirburst())
                .put("explosionAirburst", w.explosionAirburst())
                .put("explosionThroughWall", x.throughWall())
                .put("explosionThroughWallFactor", x.throughWallFactor())
                .put("isNewExplosionBreak", x.newBreak())
                .put("proximityFuseDist", x.proximityFuseDist());
    }

    private static void camera(LegacyObject.Builder view, WeaponSpec w, WeaponDefinition.Camera c) {
        float[] zoom = new float[c.zoom().size()];
        for (int i = 0; i < zoom.length; i++) {
            zoom[i] = c.zoom().get(i);
        }
        view.put("mortarRadarMaxDist", c.mortarRadarMaxDist())
                .put("displayMortarDistance", c.displayMortarDistance())
                .put("fixCameraPitch", c.fixPitch())
                .put("cameraRotationSpeedPitch", c.rotationSpeedPitch())
                .put("ccip", w.ccip())
                .put("ccipFactor", c.ccipFactor())
                .put("lockEntity", c.lockEntity())
                .put("sight", c.sight())
                .put("zoom", zoom);
    }

    private static void missile(LegacyObject.Builder view, WeaponDefinition.Missile m) {
        WeaponDefinition.LockOn l = m.lockOn();
        view.put("laserGuidance", m.laserGuidance())
                .put("hasLaserGuidancePod", m.hasLaserGuidancePod())
                .put("enableOffAxis", m.offAxis())
                .put("isGuidedTorpedo", m.guidedTorpedo())
                .put("predictTargetPos", m.predictTargetPos())
                .put("enableBVR", m.enableBvr())
                .put("minRangeBVR", m.minRangeBvr())
                .put("scanInterval", m.scanInterval())
                .put("nameOnRWR", m.nameOnRwr())
                .put("isGPSMissile", m.gpsMissile())
                .put("tickEndHoming", m.tickEndHoming())
                .put("pdHDNMaxDegree", m.pdhdnMaxDegree())
                .put("pdHDNMaxDegreeLockOutCount", m.pdhdnMaxDegreeLockOutCount())
                .put("turningFactor", m.turningFactor())
                .put("maxDegreeOfMissile", m.maxDegreeOfMissile())
                .put("canBeIntercepted", m.canBeIntercepted())
                .put("canister", m.ballistic().canister())
                .put("dragInAir", m.ballistic().dragInAir())
                .put("antiRadiationMissile", m.arm().antiRadiationMissile())
                .put("canLockMissile", l.canLockMissile())
                .put("canLockInWater", l.lockInWater())
                .put("lockTime", l.time())
                .put("rigidityTime", l.delay())
                .put("maxLockOnRange", l.maxRange())
                .put("maxLockOnAngle", l.maxAngle())
                .put("lockMinHeight", l.minHeight())
                .put("passiveRadarLockOutCount", l.passiveRadarLockOutCount())
                .put("numLockedChaffMax", l.lockedChaffMax())
                .put("proximityFuseTick", l.proximityFuseTick())
                .put("proximityFuseDamage", l.proximityFuseDamage())
                .put("proximityFuseHeight", l.proximityFuseHeight())
                .put("isRadarMissile", m.radar().radarMissile())
                .put("activeRadar", m.radar().activeRadar())
                .put("passiveRadar", m.radar().passiveRadar())
                .put("isHeatSeekerMissile", m.heat().heatMissile())
                .put("heatCount", m.heat().heatCount())
                .put("maxHeatCount", m.heat().maxHeatCount())
                .put("antiFlareCount", m.heat().antiFlareCount());
    }

    private static void ammo(LegacyObject.Builder view, WeaponSpec w, WeaponDefinition.Ammo a) {
        List<LegacyObject> rounds = new ArrayList<>();
        for (AmmoItem item : a.items()) {
            ResourceLocation name = ResourceLocation.tryParse(item.item().toString().trim().toLowerCase(Locale.ROOT));
            rounds.add(LegacyObject.of("com.ivomens.mcheli.combat.weapon.WeaponInfo$RoundItem")
                    .put("num", item.count())
                    .put("itemName", name != null ? name : ResourceLocation.withDefaultNamespace("air"))
                    .put("damage", item.meta())
                    .put("itemStack", ItemStack.EMPTY)
                    .build());
        }
        view.put("reloadTime", a.reloadTime())
                .put("maxAmmo", a.maxAmmo())
                .put("round", w.round())
                .put("suppliedNum", a.resupplyCount())
                .put("roundItems", rounds);
    }

    private static void render(LegacyObject.Builder view, WeaponSpec w, WeaponDefinition.Render r) {
        view.put("listMuzzleFlash", w.muzzleFlashes())
                .put("listMuzzleFlashSmoke", w.muzzleSmoke())
                .put("cartridge", r.spentCasings() == null ? null : Cartridge.of(r.spentCasings()))
                .put("bulletModelName", r.bulletModel())
                .put("bulletModel", null)
                .put("bombletModelName", r.bombletModel())
                .put("bombletModel", null)
                .put("trajectoryParticleName", r.trajectoryParticle())
                .put("trajectoryParticleStartTick", r.trajectoryParticleStartTick())
                .put("flakParticlesCrack", r.flakParticlesCrack())
                .put("numParticlesFlak", r.particlesFlak())
                .put("disableSmoke", r.smoke().disabled())
                .put("smokeSize", r.smoke().size())
                .put("smokeNum", r.smoke().count())
                .put("smokeMaxAge", r.smoke().maxAge())
                .put("colorInWater", w.bulletColorInWater())
                .put("color", w.bulletColor());
    }

    private static void submunition(LegacyObject.Builder view, WeaponSpec w, WeaponDefinition.Submunition s) {
        view.put("bomblet", s.count())
                .put("bombletSTime", w.bombletSTime())
                .put("bombletDiff", s.spread())
                .put("spawnBulletInAir", s.spawnInAir())
                .put("spawnBulletMaxNum", s.spawnMaxNum())
                .put("spawnBulletIntervalTick", w.spawnBulletIntervalTick())
                .put("spawnBulletPerNum", s.spawnPerNum())
                .put("spawnBulletInheritSpeed", s.spawnInheritSpeed())
                .put("destructAfterSpawnBullet", s.destructAfterSpawn())
                .put("ahead", s.ahead());
    }

    /** Applies {@code view} to every element. */
    static <T> List<Object> map(List<T> list, Function<T, Object> view) {
        List<Object> out = new ArrayList<>();
        list.forEach(element -> out.add(view.apply(element)));
        return out;
    }
}
