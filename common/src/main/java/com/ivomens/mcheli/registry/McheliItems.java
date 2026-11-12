package com.ivomens.mcheli.registry;

import net.minecraft.world.item.Item;

/**
 * The items MCHeli always has, whatever the content packs define. {@code McheliMod} registers them;
 * the items content files define are reached through their definitions instead.
 */
public final class McheliItems {

    public static final RegistryRef<Item> SPAWN_GUNNER_VS_MONSTER = RegistryRef.item("spawn_gunner_vs_monster");
    public static final RegistryRef<Item> SPAWN_GUNNER_VS_PLAYER = RegistryRef.item("spawn_gunner_vs_player");
    public static final RegistryRef<Item> RANGEFINDER = RegistryRef.item("rangefinder");
    public static final RegistryRef<Item> WRENCH = RegistryRef.item("wrench");
    public static final RegistryRef<Item> FUEL = RegistryRef.item("fuel");
    public static final RegistryRef<Item> GLTD = RegistryRef.item("gltd");
    public static final RegistryRef<Item> CHAIN = RegistryRef.item("chain");
    public static final RegistryRef<Item> PARACHUTE = RegistryRef.item("parachute");
    public static final RegistryRef<Item> CONTAINER = RegistryRef.item("container");
    public static final RegistryRef<Item> UAV_STATION = RegistryRef.item("uav_station");
    public static final RegistryRef<Item> SMALL_UAV_STATION = RegistryRef.item("uav_station2");
    public static final RegistryRef<Item> UAV_PAIRING_DEVICE = RegistryRef.item("uav_pairing_device");
    public static final RegistryRef<Item> UAV_TABLET = RegistryRef.item("uav_tablet");
    public static final RegistryRef<Item> MINE_DEFUSER = RegistryRef.item("mine_defuser");
    /** A placeholder that renders as nothing; in no creative tab. */
    public static final RegistryRef<Item> INVISIBLE = RegistryRef.item("internal");
    public static final RegistryRef<Item> STINGER_MISSILE = RegistryRef.item("fim92_bullet");
    public static final RegistryRef<Item> JAVELIN_MISSILE = RegistryRef.item("fgm148_bullet");
    public static final RegistryRef<Item> STINGER = RegistryRef.item("fim92");
    public static final RegistryRef<Item> JAVELIN = RegistryRef.item("fgm148");

    private McheliItems() {
    }
}
