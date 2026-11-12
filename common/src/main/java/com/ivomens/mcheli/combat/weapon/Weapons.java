package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.content.def.weapon.AmmoItem;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.recipe.LegacyItemIds;
import com.ivomens.mcheli.content.registry.ContentBindings.RoundStack;
import com.ivomens.mcheli.content.registry.ContentBindings;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentRegistries;
import com.ivomens.mcheli.content.registry.ContentRegistry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.sound.SoundRegistry;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.util.state.RegistryHolder;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.ivomens.mcheli.compat.ModCompatManager.MODID_HBM;
import static com.ivomens.mcheli.compat.ModCompatManager.isLoaded;

/** The loaded weapon types by file name, which vehicles and launchers refer to them by. */
public final class Weapons {

    private static final ContentRegistry<WeaponDefinition, WeaponSpec> WEAPONS = new ContentRegistry<>(
            ContentType.WEAPON, WeaponDefinition.class, Weapons::load, Weapons::reloaded);
    /** The vehicle classes the named damage targets stand for, handed in at start-up. */
    @RegistryHolder
    private static Map<String, Class<? extends Entity>> vehicleTargets = Map.of();

    private Weapons() {
    }

    /**
     * Hands the weapon registry to the content pipeline; call once at start-up, before content loads.
     * {@code vehicleTargets} are the vehicle classes a weapon file's named damage targets stand for.
     */
    public static void registerContent(Map<String, Class<? extends Entity>> vehicleTargets) {
        Weapons.vehicleTargets = Map.copyOf(vehicleTargets);
        ContentRegistries.register(WEAPONS);
    }

    public static ContentRegistry<WeaponDefinition, WeaponSpec> registry() {
        return WEAPONS;
    }

    public static Optional<WeaponSpec> get(String name) {
        return WEAPONS.get(name);
    }

    public static boolean contains(String name) {
        return WEAPONS.contains(name);
    }

    /**
     * Once every item exists: binds the items each weapon is reloaded with and the item a dispenser uses, and
     * checks for HBM payloads.
     */
    public static void onItemsRegistered() {
        WEAPONS.values().forEach(Weapons::bindItems);
        WEAPONS.values().stream().filter(WeaponSpec::useHBM).forEach(Weapons::warnWithoutHbm);
    }

    /** Registers the weapon's sounds as its file loads, then builds its run-time objects. */
    private static WeaponSpec load(ContentEntry<WeaponDefinition> entry) {
        WeaponDefinition.Locations sounds = entry.definition().sound().locations();
        SoundRegistry.INSTANCE.parseSound(sounds.fire().getPath());
        if (sounds.hit() != null) {
            SoundRegistry.INSTANCE.parseSound(sounds.hit().getPath());
        }
        SoundRegistry.INSTANCE.parseSound(sounds.railgun().getPath());
        return WeaponSpec.of(entry.definition(), vehicleTargets);
    }

    /** A weapon read again in game gets its item bindings again. */
    private static void reloaded(WeaponSpec weapon) {
        bindItems(weapon);
        warnWithoutHbm(weapon);
    }

    /** Binds the items the weapon is reloaded with and, for a dispenser, the item it uses where it lands. */
    private static void bindItems(WeaponSpec weapon) {
        bindRounds(weapon);
        bindDispensedItem(weapon);
    }

    /** A dispensed item that does not exist stays unbound, and the dispenser then uses nothing. */
    private static void bindDispensedItem(WeaponSpec weapon) {
        String dispensed = weapon.definition().dispenseItem().location();
        ResourceLocation id = dispensed != null ? ResourceLocation.tryParse(dispensed) : null;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            ContentBindings.bindDispensedItem(weapon.id(), BuiltInRegistries.ITEM.get(id));
        }
    }

    /** An ammo item that does not exist stays an empty stack. */
    private static void bindRounds(WeaponSpec weapon) {
        List<RoundStack> rounds = new ArrayList<>();
        for (AmmoItem ammo : weapon.ammo().items()) {
            rounds.add(new RoundStack(ammo.count(), roundStack(ammo)));
        }
        ContentBindings.bindRounds(weapon.id(), rounds);
    }

    private static ItemStack roundStack(AmmoItem ammo) {
        ResourceLocation written = ResourceLocation.tryParse(ammo.item().toString().trim().toLowerCase(Locale.ROOT));
        String name = written != null ? written.toString() : "minecraft:air";
        ResourceLocation id = ResourceLocation.tryParse(LegacyItemIds.legacyItemId(name, ammo.meta()));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id), 1);
    }

    private static void warnWithoutHbm(WeaponSpec weapon) {
        if (weapon.useHBM() && !isLoaded(MODID_HBM)) {
            ModLogger.get().warn(
                    "Weapon:\"{}\" uses HBM capabilities, to use it please install HBM:NTM Community Edition",
                    weapon.name());
        }
    }
}
