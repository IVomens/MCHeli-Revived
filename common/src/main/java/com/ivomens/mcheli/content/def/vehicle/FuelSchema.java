package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.VehicleFile.Fuel;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.KeyAlias;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.Map;

import static com.ivomens.mcheli.core.content.schema.Codecs.BOOLEAN;
import static com.ivomens.mcheli.core.content.schema.Codecs.TEXT;
import static com.ivomens.mcheli.core.content.schema.Codecs.floats;
import static com.ivomens.mcheli.core.content.schema.Codecs.ints;

/**
 * Fuel, at the top of the file and in two sections:
 * <pre>
 * MaxFuel: 1200
 * FuelType: { mch_fuel: 1.0, kerosene: 0.8 }   # or one fluid name
 * FuelConsumption: 1.0
 * FuelSupplyRange: 35.0          # refuels vehicles this close; 0 for none
 * FuelSupply: { Infinite: true, Type: mch_fuel }
 * Electric: { Battery: true }    # or Electric: true; runs on charge instead of fluid
 * </pre>
 */
final class FuelSchema {

    /** Fuel the tank holds; 0 for a vehicle that needs no fuel. */
    static final Field<VehicleFile, Integer> MAX_FUEL =
            Field.of("MaxFuel", ints(0, 100_000_000), 0, (VehicleFile file) -> file.fuel().maxFuel()).txt("maxfuel");
    static final Field<VehicleFile, Map<String, Float>> FUEL_TYPES = Field.of("FuelType", VehicleCodecs.FUEL_TYPES,
            VehicleCodecs.defaultFuelTypes(), (VehicleFile file) -> file.fuel().types());
    /** How fast the vehicle burns fuel. */
    static final Field<VehicleFile, Float> CONSUMPTION = Field.of("FuelConsumption", floats(0, 10_000), 1.0F,
            (VehicleFile file) -> file.fuel().consumption()).txt("fuelconsumption");
    /** Also {@code FuelSupply.Range}, or {@code FuelSupply} written as a number. */
    static final Field<VehicleFile, Float> SUPPLY_RANGE = Field.of("FuelSupplyRange", floats(0, 1000), 0.0F,
            (VehicleFile file) -> file.fuel().supplyRange()).txt("fuelsupplyrange");

    static final Field<Fuel, Boolean> INFINITE_SUPPLY =
            Field.of("Infinite", BOOLEAN, false, Fuel::infiniteSupply);
    static final Field<Fuel, String> SUPPLY_TYPE = Field.of("Type", TEXT, "mch_fuel", Fuel::supplyType);
    static final Section<VehicleFile, Fuel> SUPPLY = Section.of("FuelSupply", VehicleFile::fuel,
            INFINITE_SUPPLY, new KeyAlias<>("Range", SUPPLY_RANGE), SUPPLY_TYPE).shorthand(SUPPLY_RANGE);

    static final Field<Fuel, Boolean> ELECTRIC = Field.of("Enabled", BOOLEAN, false, Fuel::electric);
    static final Field<Fuel, Boolean> BATTERIES = Field.of("Battery", BOOLEAN, false, Fuel::batteries);
    static final Section<VehicleFile, Fuel> ELECTRIC_SECTION = Section.of("Electric", VehicleFile::fuel, ELECTRIC,
            BATTERIES).enabledBy(ELECTRIC).shorthand(ELECTRIC);

    private FuelSchema() {
    }

    static Fuel build(Values v) {
        return new Fuel(v.get(MAX_FUEL), v.get(FUEL_TYPES), v.get(CONSUMPTION), v.get(SUPPLY_RANGE),
                v.get(INFINITE_SUPPLY), v.get(SUPPLY_TYPE), v.get(ELECTRIC), v.get(BATTERIES));
    }
}
