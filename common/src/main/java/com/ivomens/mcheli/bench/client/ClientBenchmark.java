package com.ivomens.mcheli.bench.client;

import com.ivomens.mcheli.bench.SystemInfo;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The in-game client benchmark ({@code /mcheli benchmark client}): runs the steps of a
 * {@link BenchmarkPlan} on the player's own machine, shows its progress, can be cancelled with
 * Backspace (or by opening the pause menu), then puts every setting back, removes the scene and writes
 * its results to {@code <game dir>/mcheli/benchmarks/}. One instance per client; client thread only.
 */
public final class ClientBenchmark {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    @Nullable
    private BenchmarkRun run;
    @Nullable
    private ClientSettings saved;
    private Map<String, String> graphics = Map.of();
    private LocalDateTime startedAt = LocalDateTime.now();
    private long startedNanos;
    /** Whether a run cut short last time has been looked for (on the first client tick). */
    private boolean recoveryChecked;

    public void start(boolean quick) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (this.run != null || mc.player == null || mc.level == null || server == null) {
            chat(Component.literal(this.run != null ? "The benchmark is already running."
                    : "The client benchmark only runs in a single player world.").withStyle(ChatFormatting.RED));
            return;
        }
        BenchmarkPlan plan = BenchmarkPlan.create(quick, Services.PLATFORM.isModLoaded(GraphicsInfo.FLYWHEEL));
        this.saved = ClientSettings.capture(mc);
        BenchmarkRecovery.begin(mc.gameDirectory.toPath(), this.saved);
        ClientSettings.applyBenchmark(mc);
        mc.options.broadcastOptions();
        this.graphics = GraphicsInfo.describe(mc);
        this.startedAt = LocalDateTime.now();
        this.startedNanos = System.nanoTime();
        this.run = new BenchmarkRun(mc, server, plan);
        ModLogger.info("Client benchmark started: {} steps, about {} s", plan.steps().size(), plan.estimatedSeconds());
        int minutes = Math.max(1, Math.round(plan.estimatedSeconds() / 60.0f));
        chat(Component.literal(String.format(Locale.ROOT, "MCHeli benchmark: %d steps, about %d minute%s."
                        + " Don't touch the mouse; press Backspace to cancel.", plan.steps().size(), minutes,
                minutes == 1 ? "" : "s")).withStyle(ChatFormatting.GOLD));
    }

    public void onClientTick() {
        if (!this.recoveryChecked) {
            this.recoveryChecked = true;
            BenchmarkRecovery.recover(Minecraft.getInstance());
        }
        if (this.run == null) {
            return;
        }
        String cancel = cancelReason(Minecraft.getInstance());
        if (cancel != null) {
            finish(cancel);
        } else if (this.run.tick()) {
            finish(null);
        }
    }

    public void onFrameStart() {
        if (this.run != null) {
            this.run.frameStarted(System.nanoTime());
        }
    }

    public void onRenderGui(GuiGraphics graphics) {
        BenchmarkRun r = this.run;
        if (r == null) {
            return;
        }
        int seconds = (r.ticksLeft() + 19) / 20;
        BenchmarkOverlay.draw(graphics, List.of(
                Component.literal("MCHeli benchmark - step " + r.stepNumber() + " of " + r.stepCount()),
                Component.literal(r.currentStep().describe()),
                Component.literal("").append(r.status()).append(seconds > 0 ? ", " + seconds + " s left" : ""),
                Component.literal("Backspace: cancel")), r.progress());
    }

    /** The player is leaving the world: stop, put the client settings back and keep what was measured. */
    public void onLoggingOut() {
        if (this.run != null) {
            this.run.abandonOnServer();
            finishClient("you left the world");
        }
    }

    @Nullable
    private String cancelReason(Minecraft mc) {
        if (mc.screen instanceof PauseScreen) {
            return "the pause menu was opened";
        }
        if (mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_BACKSPACE)) {
            return "Backspace was pressed";
        }
        if (mc.player == null || mc.player.isDeadOrDying()) {
            return "the player is gone";
        }
        return null;
    }

    private void finish(@Nullable String cancelled) {
        BenchmarkRun r = this.run;
        if (r == null) {
            return;
        }
        r.endOnServer();
        finishClient(cancelled);
    }

    private void finishClient(@Nullable String cancelled) {
        BenchmarkRun r = this.run;
        Minecraft mc = Minecraft.getInstance();
        this.run = null;
        if (this.saved != null) {
            this.saved.restore(mc);
            if (mc.player != null) {
                mc.options.broadcastOptions();
            }
        }
        BenchmarkRecovery.end(mc.gameDirectory.toPath());
        BenchmarkResults results = results(r, cancelled);
        try {
            Path text = results.write(mc.gameDirectory.toPath(), this.startedAt);
            ModLogger.info("Client benchmark {}; results in {}", cancelled == null ? "finished" : "cancelled", text);
            announce(results, text);
        } catch (IOException e) {
            ModLogger.error("Could not write the benchmark results", e);
            chat(Component.literal("Could not write the benchmark results: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }

    private BenchmarkResults results(BenchmarkRun r, @Nullable String cancelled) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("renderDistance", String.valueOf(ClientSettings.RENDER_DISTANCE));
        settings.put("yourRenderDistance", this.saved == null ? "?" : String.valueOf(this.saved.renderDistance()));
        settings.put("yourFramerateLimit", this.saved == null ? "?" : String.valueOf(this.saved.framerateLimit()));
        settings.put("yourVehicleRenderer", this.saved == null ? "?" : this.saved.config().render().vehicleRenderer());
        settings.put("yourFixedStepAttitude",
                this.saved == null ? "?" : String.valueOf(this.saved.config().flight().fixedStepAttitude()));
        return new BenchmarkResults(BenchmarkResults.SCHEMA, this.startedAt.format(DATE), r.quick(), cancelled != null, cancelled == null ? "" : cancelled,
                Math.round((System.nanoTime() - this.startedNanos) / 1e7) / 100.0, SystemInfo.describe(),
                this.graphics, settings, List.copyOf(r.results()));
    }

    private void announce(BenchmarkResults results, Path text) {
        String path = text.toAbsolutePath().normalize().toString();
        chat(Component.literal(results.cancelled() ? "Benchmark cancelled (" + results.cancelReason() + ")."
                : "Benchmark finished.").withStyle(results.cancelled() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        for (String line : results.comparisons()) {
            chat(Component.literal(line));
        }
        chat(Component.literal("Results: ").append(Component.literal(path).withStyle(style -> style
                .withUnderlined(true).withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, path))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy the path (the .json next to it has the same name)"))))));
    }

    private static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
