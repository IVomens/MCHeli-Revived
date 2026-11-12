package com.ivomens.mcheli.config;

/** Electric aircraft and the charger block. Server side. */
public record EnergySettings(String system, double feperEu, double feperHe, double chargerRange,
                             int chargerTransferRate, int chargerCapacity) {

    public static final StringOption SYSTEM = StringOption.of("EnergySystem", "FE")
            .describe("EnergySystem = FE | GT | HBM (external energy used by electric aircraft & the charger)");
    public static final DoubleOption FE_PER_EU = DoubleOption.of("EnergyRatioFEperEU", 4.0)
            .describe("EnergyRatioFEperEU = internal energy units gained per 1 GregTech EU (default 4)");
    public static final DoubleOption FE_PER_HE = DoubleOption.of("EnergyRatioFEperHE", 1.0)
            .describe("EnergyRatioFEperHE = internal energy units gained per 1 HBM HE (default 1)");
    public static final DoubleOption CHARGER_RANGE = DoubleOption.of("ChargerRange", 6.0);
    public static final IntOption CHARGER_TRANSFER_RATE = IntOption.of("ChargerTransferRate", 256);
    public static final IntOption CHARGER_CAPACITY = IntOption.of("ChargerCapacity", 100000);

    static EnergySettings from(ConfigValues v) {
        return new EnergySettings(v.get(SYSTEM), v.get(FE_PER_EU), v.get(FE_PER_HE), v.get(CHARGER_RANGE),
                v.get(CHARGER_TRANSFER_RATE), v.get(CHARGER_CAPACITY));
    }
}
