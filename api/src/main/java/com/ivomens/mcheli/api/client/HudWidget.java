package com.ivomens.mcheli.api.client;

/**
 * A small panel an addon draws in the in-vehicle HUD, registered with {@link HudWidgets#register}. MCHeli places
 * it in a free slot of the HUD ({@link #anchor()}), asks whether it shows for the vehicle and seat at hand
 * ({@link #isShown}), and lets it draw inside its box with the HUD's own pens ({@link #render}).
 *
 * <pre>{@code
 * public final class ThrottleWidget implements HudWidget {
 *     public HudAnchor anchor() { return HudAnchor.TOP_LEFT; }
 *     public int width() { return 90; }
 *     public int height() { return HudWidgetContext.READOUT_HEIGHT; }
 *
 *     public boolean isShown(HudWidgetContext hud) {
 *         return hud.kind() == VehicleKind.HELICOPTER && hud.seat() == 0;
 *     }
 *
 *     public void render(HudWidgetContext hud) {
 *         hud.readout(0, 0, 90, "THROTTLE", Math.round(hud.vehicle().throttle() * 100) + "%", hud.color());
 *     }
 * }
 * }</pre>
 *
 * <p><b>Coordinates.</b> Sizes and positions are in HUD units: the modern HUD lays itself out on a virtual
 * screen 640 units wide and 360 high, scaled to the window height and the player's {@code HudScale}, and widgets
 * use the same units in both HUD styles. A widget draws in its own box, with (0, 0) its top left corner and
 * ({@link #width()}, {@link #height()}) its bottom right; nothing clips what it draws outside.
 *
 * <p><b>Side and thread.</b> Implemented by addons, called by MCHeli on the client's render thread only, once
 * per frame while the HUD is drawn. A method that throws is logged once and the widget is not drawn again until
 * it is registered anew.
 *
 * <p><b>Stability.</b> Since API 1.1. Addons implement this interface; MCHeli only adds methods to it with a
 * default, so a widget written for 1.1 keeps working with every 1.x.
 */
public interface HudWidget {

    /** Which slot of the HUD the widget goes in. Read every frame, so a widget may move between slots. */
    HudAnchor anchor();

    /** The width of the widget's box in HUD units. Read every frame; a widget 0 or less wide is not drawn. */
    int width();

    /** The height of the widget's box in HUD units. Read every frame; a widget 0 or less high is not drawn. */
    int height();

    /**
     * Where the widget goes among the others of its anchor: lower first, that is nearest the screen edge or
     * corner. Widgets of the same order go by id. Read once, when the widget is registered. The default is 0.
     */
    default int order() {
        return 0;
    }

    /**
     * Whether the widget shows this frame, asked before the HUD lays the widgets out, so a widget that does not
     * show leaves its place to the next one. Typical checks are {@link HudWidgetContext#kind()} and
     * {@link HudWidgetContext#seat()}. The context's drawing methods must not be called here. The default shows
     * the widget in every vehicle and seat.
     */
    default boolean isShown(HudWidgetContext context) {
        return true;
    }

    /**
     * Draws the widget in its box. The context is only valid during this call; do not keep it.
     *
     * @param context the vehicle, the viewer and the HUD settings of this frame, and the pens to draw with
     */
    void render(HudWidgetContext context);
}
