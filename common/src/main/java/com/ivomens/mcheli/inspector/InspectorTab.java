package com.ivomens.mcheli.inspector;

/** The tabs of the vehicle inspector; every section of a snapshot belongs to one. */
public enum InspectorTab {

    /** What the vehicle is, where it came from, and its health, fuel, throttle, motion and rest state. */
    OVERVIEW("Overview"),
    /** Every component in tick order with its phase and the state it describes. */
    COMPONENTS("Components"),
    /** Each weapon: its id, ammunition, heat, cooldown and lock. */
    WEAPONS("Weapons"),
    /** Each seat and who sits in it. */
    CREW("Crew"),
    /** The vehicle file's values, with the ones that differ from the schema's defaults marked. */
    DEFINITION("Definition");

    private final String title;

    InspectorTab(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
