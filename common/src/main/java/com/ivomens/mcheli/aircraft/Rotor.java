package com.ivomens.mcheli.aircraft;

/** A helicopter rotor: a set of evenly spaced blades that spin together and can fold for storage. */
public class Rotor {

    private Blade[] blades;
    private boolean folded;
    private boolean foldTarget;
    private final boolean canFold;

    public Blade[] blades() {
        return this.blades;
    }

    /**
     * The position and rotation arguments are not used by the rotor itself (the renderer reads them
     * from the info); they stay for the constructor signature the helicopter uses.
     */
    public Rotor(int bladeCount, int bladeSpacing, int foldSpeed, float posX, float posY, float posZ,
                 float rotX, float rotY, float rotZ, boolean canFoldBlade) {
        this.canFold = canFoldBlade;
        this.setupBlade(bladeCount, bladeSpacing, foldSpeed);
    }

    /** Creates the blades {@code bladeSpacing} degrees apart, each folding a little further out. */
    public void setupBlade(int bladeCount, int bladeSpacing, int foldSpeed) {
        int spacing = bladeSpacing > 0 ? bladeSpacing : 1;
        this.blades = new Blade[bladeCount > 0 ? bladeCount : 1];
        for (int i = 0; i < this.blades.length; i++) {
            this.blades[i] = new Blade(i * spacing);
            this.blades[i].setFoldRotation(5 + i * 3).setFoldSpeed(foldSpeed);
        }
    }

    public boolean isFoldingOrUnfolding() {
        return this.folded != this.foldTarget;
    }

    public void startUnfold() {
        this.foldTarget = false;
    }

    public void startFold() {
        if (this.canFold) {
            this.foldTarget = true;
        }
    }

    public void forceFold() {
        if (this.canFold) {
            this.foldTarget = true;
            this.folded = true;
            for (Blade blade : this.blades) {
                blade.forceFold();
            }
        }
    }

    /** Spins the blades with the rotor angle, or moves them on towards the folded/unfolded position. */
    public void update(float rotorRotation) {
        boolean transitionDone = true;
        for (Blade blade : this.blades) {
            blade.setPrevRotation(blade.getRotation());
            if (!this.folded) {
                if (!this.foldTarget) {
                    blade.setRotation(rotorRotation + blade.getBaseRotation());
                    transitionDone = false;
                } else if (!blade.folding()) {
                    transitionDone = false;
                }
            } else if (this.foldTarget || !blade.unfolding(rotorRotation)) {
                transitionDone = false;
            }
        }
        if (transitionDone) {
            this.folded = this.foldTarget;
        }
    }
}
