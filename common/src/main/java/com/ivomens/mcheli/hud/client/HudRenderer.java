package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.hud.Hud;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.core.hud.HudVariable;
import com.ivomens.mcheli.core.hud.expr.Formula;
import com.ivomens.mcheli.hud.script.HudInstruction.Point;
import com.ivomens.mcheli.hud.script.HudInstruction;
import com.ivomens.mcheli.util.ModLogger;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.core.math.Vec2d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.function.Function;

/**
 * Runs compiled HUD scripts against a frame and draws them on a canvas.
 * <p>
 * The rules scripts were written for:
 * <ul>
 *     <li><b>Gates.</b> A format 1 {@code if} sets a gate that hides the commands after it while its condition
 *     is 0, and any {@code endif} opens it again. Gates do not count nesting; format 1 has none to count.
 *     Format 2 blocks nest, and are jumps.</li>
 *     <li><b>{@code Exit}</b> ends the script it is in; a script that called it carries on.</li>
 *     <li><b>{@code Call}</b> runs another HUD's script in place, at its own scale: a called HUD that ignores auto
 *     scaling draws at the design size inside one that does not. A script that is already running (a HUD that
 *     calls itself, directly or not) is skipped.</li>
 *     <li><b>The colour</b> carries over to the next frame, through the {@link HudSession}.</li>
 *     <li><b>A failure</b> stops the script that failed and marks it broken in the session: it is logged once and
 *     not drawn again until the HUDs reload. A {@code DrawString} whose format does not fit its values is the
 *     exception: it is logged once and skipped, and the rest of its HUD still draws.</li>
 * </ul>
 * The renderer belongs to one viewer and keeps only scratch state for the frame it draws.
 */
public final class HudRenderer {

    /** Radar contact coordinates span this many units across the scope. */
    private static final double RADAR_RANGE_UNITS = 128.0;

    private final Function<String, Hud> huds;
    private final HudSession session = new HudSession();
    private final HudViewport viewport = new HudViewport();
    private final ScriptGauges gauges = new ScriptGauges();
    /** The scripts running in this frame, outermost first. */
    private final List<Hud> running = new ArrayList<>();
    /** {@code String.format} arguments by count, reused. */
    private final List<Object[]> textArguments = new ArrayList<>();
    private double[] points = new double[16];

    private HudFrame frame;
    private HudCanvas canvas;
    private boolean autoScaleRequested;

    /** @param huds finds the HUD a {@code Call} names, or null */
    public HudRenderer(Function<String, Hud> huds) {
        this.huds = huds;
    }

    public HudSession session() {
        return this.session;
    }

    /**
     * Draws one frame of {@code hud}. The frame's {@link HudVariable#COLOR} is set to the carried colour first.
     *
     * @param autoScaleRequested whether the player scales HUDs with the window; a HUD may ignore it
     */
    public void render(Hud hud, HudFrame frame, HudCanvas canvas, boolean autoScaleRequested) {
        if (this.session.isBroken(hud)) return;
        this.frame = frame;
        this.canvas = canvas;
        this.autoScaleRequested = autoScaleRequested;
        frame.set(HudVariable.COLOR, (double) this.session.color());
        this.viewport.configure(canvas.width(), canvas.height(), autoScaleRequested && !hud.ignoreAutoScale());
        try {
            run(hud);
        } finally {
            this.frame = null;
            this.canvas = null;
        }
    }

    private void run(Hud hud) {
        if (this.running.contains(hud)) return;
        this.running.add(hud);
        try {
            execute(hud.script().code());
        } catch (RuntimeException e) {
            this.session.markBroken(hud);
            ModLogger.error("HUD script error in " + hud.fileName(), e);
        } finally {
            this.running.removeLast();
        }
    }

    private void execute(List<HudInstruction> code) {
        boolean gateOpen = true;
        for (int next = 0; next < code.size(); next++) {
            HudInstruction instruction = code.get(next);
            switch (instruction) {
                case HudInstruction.Gate gate -> gateOpen = evaluate(gate.condition()) != 0.0;
                case HudInstruction.EndGate end -> gateOpen = true;
                case HudInstruction.Branch branch -> {
                    if (evaluate(branch.condition()) == 0.0) next = branch.target() - 1;
                }
                case HudInstruction.Jump jump -> next = jump.target() - 1;
                case HudInstruction.Exit exit -> {
                    if (gateOpen) return;
                }
                default -> {
                    if (gateOpen) draw(instruction);
                }
            }
        }
    }

    private void draw(HudInstruction instruction) {
        switch (instruction) {
            case HudInstruction.Call call -> call(call.hud());
            case HudInstruction.SetColor color -> setColor((int) (long) evaluate(color.argb()));
            case HudInstruction.DrawText text -> text(text);
            case HudInstruction.FillRect rect -> fillRect(rect);
            case HudInstruction.DrawLine line -> {
                int length = place(line.points());
                this.canvas.lines(this.points, length, LineMode.LINE_STRIP, color());
            }
            case HudInstruction.DrawDashedLines line -> {
                int length = place(line.points());
                int factor = (int) evaluate(line.factor()) * this.canvas.guiScale();
                this.canvas.dashedLines(this.points, length, LineMode.LINES, color(), factor,
                        (int) evaluate(line.pattern()));
            }
            case HudInstruction.DrawTexture texture -> texture(texture);
            case HudInstruction.DrawRadar radar -> radar(radar);
            case HudInstruction.DrawGraduation scale -> graduation(scale);
            case HudInstruction.DrawCameraIndicator camera -> cameraIndicator(camera);
            case HudInstruction.Gate gate -> throw unexpected(gate);
            case HudInstruction.EndGate end -> throw unexpected(end);
            case HudInstruction.Branch branch -> throw unexpected(branch);
            case HudInstruction.Jump jump -> throw unexpected(jump);
            case HudInstruction.Exit exit -> throw unexpected(exit);
        }
    }

    private void call(String name) {
        Hud hud = this.huds.apply(name);
        if (hud == null || this.session.isBroken(hud)) return;
        boolean callerAutoScaled = this.viewport.autoScaled();
        boolean calleeAutoScaled = this.autoScaleRequested && !hud.ignoreAutoScale();
        boolean switchScale = calleeAutoScaled != callerAutoScaled;
        if (switchScale) this.viewport.configure(this.canvas.width(), this.canvas.height(), calleeAutoScaled);
        run(hud);
        if (switchScale) this.viewport.configure(this.canvas.width(), this.canvas.height(), callerAutoScaled);
    }

    private void setColor(int argb) {
        this.session.setColor(argb);
        this.frame.set(HudVariable.COLOR, (double) argb);
    }

    private void text(HudInstruction.DrawText text) {
        if (text.formatProblem() != null) return;
        int x = (int) this.viewport.x(evaluate(text.position().x()));
        int y = (int) this.viewport.y(evaluate(text.position().y()));
        if (text.needsWeapon() && !this.frame.hasWeapon()) return;
        if (text.needsMagazine() && this.frame.magazineSize() <= 0) return;

        Object[] arguments = textArguments(text.values().size());
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = this.frame.text(text.values().get(i));
        }
        String formatted;
        try {
            formatted = String.format(text.format(), arguments);
        } catch (IllegalFormatException e) {
            if (!this.session.alreadyReported(text)) {
                ModLogger.warn("HUD text \"{}\" in {} line {} does not fit its values ({}); it is not drawn",
                        text.format(), this.running.getLast().fileName(), text.line(), e.toString());
            }
            return;
        }
        if (text.centered()) {
            this.canvas.centeredText(formatted, x, y, color());
        } else {
            this.canvas.text(formatted, x, y, color(), true);
        }
    }

    private Object[] textArguments(int count) {
        while (this.textArguments.size() <= count) {
            this.textArguments.add(new Object[this.textArguments.size()]);
        }
        return this.textArguments.get(count);
    }

    /** The size is cut to whole pixels, as the first renderer did. */
    private void fillRect(HudInstruction.FillRect rect) {
        double x = this.viewport.x(evaluate(rect.position().x()));
        double y = this.viewport.y(evaluate(rect.position().y()));
        this.canvas.fillRect(x + (int) evaluate(rect.size().x()), y + (int) evaluate(rect.size().y()), x, y, color());
    }

    private void texture(HudInstruction.DrawTexture texture) {
        if (texture.texture() == null) {
            throw new IllegalStateException("'" + texture.name() + "' is not a valid texture name");
        }
        int[] size = this.session.textureSize(texture, texture.texture(), this.canvas);
        double x = this.viewport.x(evaluate(texture.position().x()));
        double y = this.viewport.y(evaluate(texture.position().y()));
        double width = evaluate(texture.size().x());
        double height = evaluate(texture.size().y());
        double u = evaluate(texture.uvPosition().x());
        double v = evaluate(texture.uvPosition().y());
        double uWidth = evaluate(texture.uvSize().x());
        double vHeight = evaluate(texture.uvSize().y());
        float rotation = (float) evaluate(texture.rotation());
        this.canvas.textureRegion(texture.texture(), x, y, width, height, u, v, uWidth, vHeight, rotation, size[0],
                size[1]);
    }

    private void radar(HudInstruction.DrawRadar radar) {
        List<Vec2d> contacts = this.frame.radarContacts(radar.allContacts());
        if (contacts == null || contacts.isEmpty()) return;
        float rotation = (float) evaluate(radar.rotation());
        double left = this.viewport.x(evaluate(radar.position().x()));
        double top = this.viewport.y(evaluate(radar.position().y()));
        double width = evaluate(radar.size().x());
        double height = evaluate(radar.size().y());

        int length = contacts.size() * 2;
        double[] dots = buffer(length);
        double scaleX = width / RADAR_RANGE_UNITS;
        double scaleY = height / RADAR_RANGE_UNITS;
        for (int i = 0; i < contacts.size(); i++) {
            dots[i * 2] = contacts.get(i).x() * scaleX;
            dots[i * 2 + 1] = contacts.get(i).y() * scaleY;
        }
        Rotations.rotatePoints(dots, rotation);

        // Keep the dots inside the scope, compacting the visible ones to the front of the buffer.
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        int visible = 0;
        for (int i = 0; i < length; i += 2) {
            double x = dots[i];
            double y = dots[i + 1];
            if (x > -halfWidth && x < halfWidth && y > -halfHeight && y < halfHeight) {
                dots[visible++] = x + left + halfWidth;
                dots[visible++] = y + top + halfHeight;
            }
        }
        this.canvas.points(dots, visible, color(), this.canvas.guiScale() * 2);
    }

    /** The scale is rolled about its own position. */
    private void graduation(HudInstruction.DrawGraduation scale) {
        this.canvas.pushPose();
        try {
            int x = (int) this.viewport.x(evaluate(scale.position().x()));
            int y = (int) this.viewport.y(evaluate(scale.position().y()));
            this.canvas.translate(x, y);
            this.canvas.rotate((float) evaluate(scale.roll()));
            this.canvas.translate(-x, -y);
            if (scale.type() != null) {
                this.gauges.graduation(this.canvas, scale.type(), evaluate(scale.rotation()), x, y, color(),
                        this.canvas.guiScale());
            }
        } finally {
            this.canvas.popPose();
        }
    }

    private void cameraIndicator(HudInstruction.DrawCameraIndicator camera) {
        double x = this.viewport.x(evaluate(camera.position().x()));
        double y = this.viewport.y(evaluate(camera.position().y()));
        if (!this.frame.hasCamera()) return;
        this.gauges.cameraIndicator(this.canvas, x, y, this.frame.vehicleYaw(), this.frame.cameraYaw(),
                this.frame.cameraPitch(), color(), this.canvas.guiScale());
    }

    /** Evaluates the points into the point buffer, as canvas coordinates; returns the number of values. */
    private int place(List<Point> placed) {
        double[] buffer = buffer(placed.size() * 2);
        for (int i = 0; i < placed.size(); i++) {
            buffer[i * 2] = this.viewport.x(evaluate(placed.get(i).x()));
            buffer[i * 2 + 1] = this.viewport.y(evaluate(placed.get(i).y()));
        }
        return placed.size() * 2;
    }

    private double[] buffer(int length) {
        if (this.points.length < length) {
            this.points = new double[length];
        }
        return this.points;
    }

    private double evaluate(Formula formula) {
        return formula.evaluate(this.frame);
    }

    private int color() {
        return this.session.color();
    }

    private static IllegalStateException unexpected(@Nullable HudInstruction instruction) {
        return new IllegalStateException("not a drawing instruction: " + instruction);
    }
}
