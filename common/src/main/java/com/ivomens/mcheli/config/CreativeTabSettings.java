package com.ivomens.mcheli.config;

/** The item each creative tab shows as its icon, by content name. Client side. */
public record CreativeTabSettings(String items, String helicopters, String planes, String ships, String tanks,
                                  String vehicles) {

    public static final StringOption ITEMS = StringOption.of("CreativeTabIconItem", "fuel");
    public static final StringOption HELICOPTERS = StringOption.of("CreativeTabIconHeli", "ah-64");
    public static final StringOption PLANES = StringOption.of("CreativeTabIconPlane", "f22a");
    public static final StringOption SHIPS = StringOption.of("CreativeTabIconShip", "project1204");
    public static final StringOption TANKS = StringOption.of("CreativeTabIconTank", "merkava_mk4");
    public static final StringOption VEHICLES = StringOption.of("CreativeTabIconVehicle", "mk15");

    static CreativeTabSettings from(ConfigValues v) {
        return new CreativeTabSettings(v.get(ITEMS), v.get(HELICOPTERS), v.get(PLANES), v.get(SHIPS), v.get(TANKS),
                v.get(VEHICLES));
    }
}
