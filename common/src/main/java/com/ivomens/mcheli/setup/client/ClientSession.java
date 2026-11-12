package com.ivomens.mcheli.setup.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.client.WeaponWheel;
import com.ivomens.mcheli.bench.client.ClientBenchmark;
import com.ivomens.mcheli.client.camera.PostEffects;
import com.ivomens.mcheli.client.camera.ViewEntity;
import com.ivomens.mcheli.client.model.ItemRenderContext;
import com.ivomens.mcheli.combat.explosion.client.ExplosionDetailReporter;
import com.ivomens.mcheli.combat.weapon.client.LockWarningTone;
import com.ivomens.mcheli.combat.weapon.client.ViewRecoil;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.equipment.launcher.client.HandheldWeaponSession;
import com.ivomens.mcheli.equipment.rangefinder.client.RangeFinderScope;
import com.ivomens.mcheli.hud.client.instrument.BvrLockMarkers;
import com.ivomens.mcheli.inspector.client.InspectorClient;
import com.ivomens.mcheli.hud.client.instrument.GpsMarker;
import com.ivomens.mcheli.hud.client.instrument.Instrument;
import com.ivomens.mcheli.hud.client.instrument.MortarRadar;
import com.ivomens.mcheli.hud.client.instrument.RadarWarningReceiver;
import com.ivomens.mcheli.livery.client.LiveryClient;
import com.ivomens.mcheli.livery.client.LiveryTextures;
import com.ivomens.mcheli.multiplay.TargetMarkerObject;
import com.ivomens.mcheli.multiplay.client.OperatorReports;
import com.ivomens.mcheli.multiplay.client.TeamMarkers;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.client.render.ThermalVision;
import com.ivomens.mcheli.fx.client.ClientEffectSpawner;
import com.ivomens.mcheli.radar.client.EntityInfoClientTracker;
import com.ivomens.mcheli.vehicle.client.OnDemandModels;
import com.ivomens.mcheli.vehicle.client.VehicleControlContext;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import com.ivomens.mcheli.vehicle.client.input.GlfwJoystickSource;
import com.ivomens.mcheli.vehicle.client.input.JoystickInput;
import com.ivomens.mcheli.vehicle.client.input.PilotControls;
import com.ivomens.mcheli.vehicle.client.input.PitchLimit;
import com.ivomens.mcheli.vehicle.client.render.SearchLights;
import com.ivomens.mcheli.vehicle.client.render.VehicleTextures;
import com.ivomens.mcheli.world.uavstation.client.UavCameraFeed;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Everything one game client keeps between ticks and frames: the camera and view, the input state the
 * vehicle controls share, the handheld launcher, the warning tones, and (once the game is set up) the tick
 * loop with its handlers and overlays. Features get the parts they need handed in; only the client's setup
 * code reaches the session itself.
 *
 * <p>One per client ({@link McheliClient#session()}). Constructing it touches neither the game nor the
 * config; the tick loop is created by {@link #start} once both are ready.
 */
public final class ClientSession {

    private final PostEffects postEffects = new PostEffects();
    /** The matrices of the level frame drawn last, for the HUD's markers. */
    private final ScreenProjection screenProjection = new ScreenProjection();
    private final TeamMarkers teamMarkers = new TeamMarkers(this.screenProjection);
    private final ThermalVision thermalVision = new ThermalVision();
    private final ClientEffectSpawner effectSpawner = new ClientEffectSpawner();
    private final ViewEntity view = new ViewEntity(this.postEffects);
    private final VehicleControlContext vehicleControls = new VehicleControlContext(new VehicleCamera(), this.view,
            new PitchLimit(), new WeaponWheel(), new PilotControls(new JoystickInput(new GlfwJoystickSource())));
    private final HandheldWeaponSession launcher = new HandheldWeaponSession(this.screenProjection);
    private final LockWarningTone lockWarningTone = new LockWarningTone();
    private final ViewRecoil viewRecoil = new ViewRecoil();
    private final SearchLights searchLights = new SearchLights();
    private final RangeFinderScope rangeFinder = new RangeFinderScope(this.vehicleControls.camera());
    private final SearchLightRelight searchLightRelight = new SearchLightRelight(this.searchLights);
    private final OperatorReports operatorReports = new OperatorReports();
    private final LiveryClient livery = new LiveryClient();
    private final EntityInfoClientTracker radarContacts = new EntityInfoClientTracker();
    /** Every vehicle carries the same instruments; each one decides for itself whether it shows. */
    private final List<Instrument> instruments = List.of(new GpsMarker(this.screenProjection),
            new MortarRadar(this.radarContacts), new BvrLockMarkers(this.radarContacts, this.screenProjection),
            new RadarWarningReceiver());
    private final OnDemandModels.Sweeper modelSweeper = new OnDemandModels.Sweeper();
    private final UavCameraFeed uavFeed = new UavCameraFeed(this.vehicleControls.camera());
    private final ItemRenderContext itemRenderContext = new ItemRenderContext();
    private final VehicleTextures vehicleTextures = new VehicleTextures(this::ridingAircraft, LiveryTextures::textureFor,
            this.thermalVision);
    private final ClientBenchmark benchmark = new ClientBenchmark();
    private final InspectorClient inspector = new InspectorClient();
    private final ExplosionDetailReporter explosionDetail = new ExplosionDetailReporter();
    @Nullable
    private ClientTicks ticks;

    ClientSession() {
    }

    /** Creates the tick loop, unless it exists already. Needs the game and the config. */
    void start(Minecraft minecraft, KeySettings bindings) {
        if (this.ticks == null) {
            this.ticks = new ClientTicks(minecraft, bindings, this);
        }
    }

    /** The tick loop, or null before {@link #start}. */
    @Nullable
    ClientTicks ticks() {
        return this.ticks;
    }

    ScreenProjection screenProjection() {
        return this.screenProjection;
    }

    TeamMarkers teamMarkers() {
        return this.teamMarkers;
    }

    ThermalVision thermalVision() {
        return this.thermalVision;
    }

    ClientEffectSpawner effectSpawner() {
        return this.effectSpawner;
    }

    PostEffects postEffects() {
        return this.postEffects;
    }

    ViewEntity view() {
        return this.view;
    }

    VehicleControlContext vehicleControls() {
        return this.vehicleControls;
    }

    HandheldWeaponSession launcher() {
        return this.launcher;
    }

    LockWarningTone lockWarningTone() {
        return this.lockWarningTone;
    }

    ViewRecoil viewRecoil() {
        return this.viewRecoil;
    }

    SearchLights searchLights() {
        return this.searchLights;
    }

    RangeFinderScope rangeFinder() {
        return this.rangeFinder;
    }

    SearchLightRelight searchLightRelight() {
        return this.searchLightRelight;
    }

    OperatorReports operatorReports() {
        return this.operatorReports;
    }

    /** The in-game benchmark ({@code /mcheli benchmark client}). */
    ClientBenchmark benchmark() {
        return this.benchmark;
    }

    /** The vehicle inspector (its key, {@code /mcheli inspect}). */
    InspectorClient inspector() {
        return this.inspector;
    }

    /** Tells the server what this client needs of explosions. */
    ExplosionDetailReporter explosionDetail() {
        return this.explosionDetail;
    }

    LiveryClient livery() {
        return this.livery;
    }

    EntityInfoClientTracker radarContacts() {
        return this.radarContacts;
    }

    List<Instrument> instruments() {
        return this.instruments;
    }

    OnDemandModels.Sweeper modelSweeper() {
        return this.modelSweeper;
    }

    UavCameraFeed uavFeed() {
        return this.uavFeed;
    }

    ItemRenderContext itemRenderContext() {
        return this.itemRenderContext;
    }

    VehicleTextures vehicleTextures() {
        return this.vehicleTextures;
    }

    /** Collects where each vehicle was drawn, for the team markers and the launcher sight. */
    void vehicleDrawn(AircraftEntity vehicle, double x, double y, double z) {
        this.teamMarkers.queue(1, TargetMarkerObject.fromEntity(vehicle), x,
                y + vehicle.spec().hitBoxes().markerHeight(), z);
        markForLauncherSight(vehicle, x, y, z);
    }

    /** Rebinds every key from the settings, once the tick loop exists. */
    void updateKeybind(KeySettings bindings) {
        if (this.ticks != null) {
            this.ticks.updateKeybind(bindings);
        }
    }

    /** The aircraft the player rides as of the current frame, or null (also before the game is set up). */
    @Nullable
    AircraftEntity ridingAircraft() {
        return this.ticks != null ? this.ticks.ridingAircraft() : null;
    }

    /** Shows a title the server sent, once the tick loop exists. */
    void showTitle(Component title, int showTime, int position) {
        if (this.ticks != null) {
            this.ticks.overlays().titles().setupTitle(title, showTime, position);
        }
    }

    /** Flashes the hit marker: one of the player's bullets hit. */
    void showHitMarker() {
        if (this.ticks != null) {
            this.ticks.overlays().hitMarker().hitBullet();
        }
    }

    /** Called while entities render: the launcher sight follows its locked target on screen. */
    void markForLauncherSight(Entity entity, double x, double y, double z) {
        if (this.ticks != null) {
            this.launcher.markEntity(entity, x, y, z);
        }
    }
}
