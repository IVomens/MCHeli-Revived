package com.ivomens.mcheli.aircraft.net;

/** A {@link ControlInputBuilder} that also collects the helicopter and ground vehicle extras. */
public class VehicleControlInputBuilder extends ControlInputBuilder {

    private int unhitchChainId = -1;
    private VehicleExtras.BladeStatus bladeStatus = VehicleExtras.BladeStatus.NONE;

    public VehicleExtras buildExtras() {
        return new VehicleExtras(unhitchChainId, bladeStatus);
    }

    /** Entity id of a tow chain to release; -1 for none. */
    public void setUnhitchChainId(int unhitchChainId) {
        this.unhitchChainId = unhitchChainId;
    }

    public void setBladeStatus(VehicleExtras.BladeStatus bladeStatus) {
        this.bladeStatus = bladeStatus;
    }
}
