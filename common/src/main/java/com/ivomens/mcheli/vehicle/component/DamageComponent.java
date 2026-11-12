package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.FriendlyFire;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.damage.ModDamageSources;
import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.event.ApiEvents;
import com.ivomens.mcheli.event.DamageEvents;
import com.ivomens.mcheli.event.PermissionEvents;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.vehicle.settings.DurabilityRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Hit points and incoming damage: armor, the part a hit lands on (with its damage factor and
 * reactive armor), players breaking the vehicle down, destruction, and the slow self-repair.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class DamageComponent implements VehicleComponent {

    /** Ticks without damage before self-repair starts, and between repair steps. */
    private static final int REPAIR_DELAY_AFTER_HIT = 600;
    private static final int REPAIR_INTERVAL = 40;
    private static final float MAX_CRASH_EXPLOSION = 15.0F;
    private static final int DEFAULT_MAX_HP = 100;

    private final AircraftEntity vehicle;
    private final SyncedValue<Integer> damageTaken;
    private final LifecycleComponent lifecycle;
    private final CountermeasureComponent countermeasures;
    private final PartBoxesComponent partBoxes;
    private final SeatsComponent seats;
    private final OwnershipComponent ownership;
    private final Supplier<DurabilityRules> durability;
    /** What turns the vehicle into a wreck once hits destroy it; see {@link #whenDestroyed}. */
    private Runnable onDestroyed = () -> {
    };
    /** Short invulnerability after some damage types, so one source cannot hit every tick. */
    private int invulnerableTicks;
    private int repairCooldown;
    private int lastDamageTaken;
    /** Set to the max HP once the wreck has blown up, so it only explodes once. */
    private int damageSinceDestroyed;
    @Nullable
    private Entity lastAttacker;

    public DamageComponent(AircraftEntity vehicle, SyncedValue<Integer> damageTaken, LifecycleComponent lifecycle,
            CountermeasureComponent countermeasures, PartBoxesComponent partBoxes, SeatsComponent seats,
            OwnershipComponent ownership, Supplier<DurabilityRules> durability) {
        this.vehicle = vehicle;
        this.damageTaken = damageTaken;
        this.lifecycle = lifecycle;
        this.countermeasures = countermeasures;
        this.partBoxes = partBoxes;
        this.seats = seats;
        this.ownership = ownership;
        this.durability = durability;
    }

    /**
     * What happens when hits destroy the vehicle, run once its HP is gone and before the wreck explodes.
     * The wreck subscribes, so the damage does not depend on it.
     */
    public void whenDestroyed(Runnable listener) {
        this.onDestroyed = listener;
    }

    public int maxHp() {
        VehicleSpec info = this.vehicle.spec();
        return info != null ? info.file().maxHp() : DEFAULT_MAX_HP;
    }

    public int hp() {
        return Math.max(maxHp() - damageTaken(), 0);
    }

    public int damageTaken() {
        return this.damageTaken.get();
    }

    public void setDamageTaken(int damage) {
        this.damageTaken.set(Mth.clamp(damage, 0, maxHp()));
    }

    @Nullable
    public Entity lastAttacker() {
        return this.lastAttacker;
    }

    public boolean isExploded() {
        return this.vehicle.isDestroyed() && this.damageSinceDestroyed > maxHp() / 10 + 1;
    }

    public void markExploded() {
        this.damageSinceDestroyed = maxHp();
    }

    /**
     * Applies a hit. A client only predicts that the hit lands; the server works out the damage,
     * the part it lands on, and what follows from it. Seats and hit boxes pass their hits on to here,
     * so the API's damage events fire once per hit whatever part of the vehicle it struck.
     */
    public boolean hurt(DamageSource source, float amount) {
        if (this.countermeasures.ironCurtain().isRunning() || shouldIgnore(source)) {
            return false;
        }
        if (onlyPredictsHits()) {
            return true;
        }
        float incoming = DamageEvents.vehicleDamaged(this.vehicle, source, amount, ModDamageSources::weaponIdOf);
        if (Float.isNaN(incoming)) {
            return false;
        }

        PartBoundingBox part = HitPartLocator.locate(this.vehicle.getBoundingBox(), source);
        Entity attacker = source.getEntity();
        float damage = applyArmor(source, incoming, part != null ? part.damageFactor() : 1.0F);
        if (attacker instanceof LivingEntity) {
            this.lastAttacker = attacker;
        }

        boolean playerBreaksIt = false;
        if (attacker instanceof Player player) {
            playerBreaksIt = isPlayerBreakingIt(player, source.getMsgId(), damage);
            if (playerBreaksIt && player.getAbilities().instabuild) {
                if (PermissionEvents.mayDismantle(this.vehicle, player)) {
                    this.lifecycle.destroy(true);
                    playDamageSound();
                    return true;
                }
                playerBreaksIt = false;
            }
        }
        if (damage <= 0.0F) {
            return false;
        }

        this.partBoxes.reactiveArmor().onHit(this.vehicle.level(), part, damage, attacker);
        if (!this.vehicle.isDestroyed()) {
            damageHull(source, attacker, damage, playerBreaksIt);
        }
        playDamageSound();
        return true;
    }

    /** A hit on a vehicle that is not a wreck yet: a player breaks it down, or it loses hit points. */
    private void damageHull(DamageSource source, @Nullable Entity attacker, float damage, boolean playerBreaksIt) {
        if (playerBreaksIt && PermissionEvents.mayDismantle(this.vehicle, (Player) attacker)) {
            this.vehicle.hurtMarked = true;
            breakDownByPlayer((Player) attacker);
            return;
        }
        setDamageTaken(damageTaken() + (int) damage);
        this.vehicle.hurtMarked = true;
        DamageEvents.vehicleDamageTaken(this.vehicle, source, ModDamageSources::weaponIdOf, damage, hp(), maxHp());
        if (damageTaken() >= maxHp()) {
            destroy(attacker, source.getMsgId());
        }
    }

    /** A client's copy of the vehicle only predicts that a hit lands; the server owns the damage. */
    private boolean onlyPredictsHits() {
        return !this.damageTaken.isWritable();
    }

    private boolean shouldIgnore(DamageSource source) {
        String type = source.getMsgId();
        return this.vehicle.isInvulnerableTo(source) || this.vehicle.isRemoved() || this.invulnerableTicks > 0
                || type.equalsIgnoreCase("inFire") || type.equalsIgnoreCase("cactus");
    }

    /**
     * Applies the external damage config, the armor values and the part's factor. Factors up to 1
     * are applied before the armor's minimum damage is subtracted, larger factors (weak spots) after.
     */
    private float applyArmor(DamageSource source, float amount, float partFactor) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        float damage = DamageScaling.byExternal(ac, source, amount);
        if (info != null && info.file().invulnerable()) {
            return 0.0F;
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.lifecycle.destroy(false);
        }
        if (!FriendlyFire.allows(source, ac)) {
            return 0.0F;
        }

        String type = source.getMsgId();
        boolean explosion = type.startsWith("explosion");
        boolean lava = type.equalsIgnoreCase("lava");
        boolean onFire = type.equalsIgnoreCase("onFire");
        if (explosion && info != null) {
            damage *= info.file().features().explosionDamageFactor();
        }
        if (lava) {
            damage *= ac.getRandom().nextInt(8) + 2;
            this.invulnerableTicks = 2;
        } else if (explosion) {
            this.invulnerableTicks = 1;
        } else if (onFire) {
            this.invulnerableTicks = 10;
        }

        if (info == null || lava || onFire) {
            return damage;
        }
        damage = Math.min(damage, info.file().armor().armorMaxDamage());
        if (partFactor <= 1.0F) {
            damage *= partFactor;
        }
        damage = damage * info.file().armor().armorDamageFactor() - info.file().armor().armorMinDamage();
        if (damage <= 0.0F) {
            return 0.0F;
        }
        if (partFactor > 1.0F) {
            damage *= partFactor;
        }
        return damage;
    }

    /**
     * True when a player's punch should pick the vehicle up instead of damaging it. Nobody but its owner and
     * operators picks up a locked vehicle; anyone else's punch is an ordinary hit.
     */
    private boolean isPlayerBreakingIt(Player player, String type, float damage) {
        if (type.equalsIgnoreCase("player") && this.ownership.mayDismantle(player)) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            VehicleSpec info = this.vehicle.spec();
            DurabilityRules rules = this.durability.get();
            if (info != null && !info.file().creativeOnly() && !rules.unbreakable()) {
                if (!rules.breakableOnlyWithPickaxe()) {
                    return !this.seats.hasPlayerAboard();
                }
                if (player.getMainHandItem().getItem() instanceof PickaxeItem) {
                    return true;
                }
            }
        }
        this.vehicle.playSound(ModSounds.getSound("hit"), damage > 0.0F ? 1.0F : 0.5F, 1.0F);
        return false;
    }

    private void destroy(@Nullable Entity attacker, String type) {
        AircraftEntity ac = this.vehicle;
        setDamageTaken(maxHp());
        this.invulnerableTicks = 20;
        this.onDestroyed.run();
        ApiEvents.vehicleDestroyed(ac, attacker);

        if (type.equalsIgnoreCase("inWall")) {
            explodeOnCrash();
            markExploded();
        } else {
            Explosions.detonate(ac.level(), wreckExplosion(ac, 2.0F, 2.0F), null, attacker);
        }
    }

    private void breakDownByPlayer(Player player) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info != null && VehicleTypes.item(info).orElse(null) != null) {
            // in creative the item only drops when sneaking is the opposite of the config default
            boolean drop = !player.getAbilities().instabuild
                    || this.durability.get().creativeDropsItem() != player.isShiftKeyDown();
            if (drop) {
                ac.spawnAtLocation(new ItemStack(VehicleTypes.item(info).orElse(null)), 0.0F);
            }
        }
        this.lifecycle.destroy(true);
    }

    private void playDamageSound() {
        this.vehicle.playSound(ModSounds.getSound("helidmg"), 1.0F, 0.9F + this.vehicle.getRandom().nextFloat() * 0.1F);
    }

    /** Crash explosion of the wreck; its size comes from explosionSizeByCrash (0 disables it). */
    public void explodeOnCrash() {
        AircraftEntity ac = this.vehicle;
        float size = ac.spec() != null ? ac.spec().file().explosionSizeByCrash() : 2.0F;
        if (size <= 0.0F) {
            return;
        }
        size = Mth.clamp(size, 1.0F, MAX_CRASH_EXPLOSION);
        Explosions.detonate(ac.level(), wreckExplosion(ac, size, size >= 2.0F ? size * 0.5F : 1.0F), null, null);
    }

    /** A wreck blows up burning: it breaks blocks, starts fires and sets the crew alight. */
    static ExplosionSpec wreckExplosion(AircraftEntity ac, float size, float blockSize) {
        return ExplosionSpec.at(ac.position(), size).blockSize(blockSize).fire(true).destroyBlocks(true)
                .igniteEntities(5).build();
    }

    /**
     * Removes up to {@code amount} damage (at least 1); false when there was nothing to repair. Only
     * the server's repair sticks, a client just learns whether there was damage.
     */
    public boolean repair(int amount) {
        if (damageTaken() <= 0) {
            return false;
        }
        setDamageTaken(damageTaken() - Math.max(amount, 1));
        return true;
    }

    /** Heals 1% HP every two seconds once the vehicle has gone 30 seconds without damage. */
    @Override
    public void serverTick(TickContext ctx) {
        if (this.invulnerableTicks > 0) {
            this.invulnerableTicks--;
        }
        if (maxHp() <= 0) {
            return;
        }
        if (!this.vehicle.isDestroyed()) {
            if (damageTaken() > this.lastDamageTaken) {
                this.repairCooldown = REPAIR_DELAY_AFTER_HIT;
            } else if (this.repairCooldown > 0) {
                this.repairCooldown--;
            } else {
                this.repairCooldown = REPAIR_INTERVAL;
                if ((double) hp() / maxHp() >= this.durability.get().autoRepairAbove()) {
                    repair(maxHp() / 100);
                }
            }
        }
        this.lastDamageTaken = damageTaken();
    }

    /** Clients only count down the invulnerability, which decides whether a predicted hit lands. */
    @Override
    public void clientTick(TickContext ctx) {
        if (this.invulnerableTicks > 0) {
            this.invulnerableTicks--;
        }
    }

    @Override
    public void describe(StateSink state) {
        state.put("hp", hp() + " / " + maxHp());
        state.put("damage taken", damageTaken());
        state.put("invulnerable ticks", this.invulnerableTicks);
        state.put("repair cooldown", this.repairCooldown);
        state.put("exploded", isExploded());
        state.put("last attacker", this.lastAttacker == null ? "-" : this.lastAttacker.getName().getString());
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt(VehicleNbtCodec.DAMAGE, damageTaken());
    }

    /** Saves from before the damage key keep the default. A wreck loads fully damaged instead (see the assembly). */
    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(VehicleNbtCodec.DAMAGE)) {
            setDamageTaken(tag.getInt(VehicleNbtCodec.DAMAGE));
        }
    }
}
