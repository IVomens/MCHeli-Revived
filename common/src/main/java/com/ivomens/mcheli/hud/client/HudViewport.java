package com.ivomens.mcheli.hud.client;

/**
 * Where script coordinates land on the canvas. A script coordinate is an offset from the screen centre in the
 * units of the 427 x 240 canvas scripts were laid out for; auto scaling stretches those units to the window.
 */
final class HudViewport {

    private static final double DESIGN_WIDTH = 427.0;
    private static final double DESIGN_HEIGHT = 240.0;

    private double centerX;
    private double centerY;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private boolean autoScaled;

    void configure(double width, double height, boolean autoScaled) {
        this.centerX = width / 2.0;
        this.centerY = height / 2.0;
        this.autoScaled = autoScaled;
        this.scaleX = autoScaled ? width / DESIGN_WIDTH : 1.0;
        this.scaleY = autoScaled ? height / DESIGN_HEIGHT : 1.0;
    }

    boolean autoScaled() {
        return this.autoScaled;
    }

    /** Canvas x of a script coordinate. */
    double x(double offsetFromCenter) {
        return this.centerX + offsetFromCenter * this.scaleX;
    }

    /** Canvas y of a script coordinate. */
    double y(double offsetFromCenter) {
        return this.centerY + offsetFromCenter * this.scaleY;
    }
}
