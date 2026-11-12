package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.ExtraBoundingBoxTracker;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * The hull's part boxes: the rotated boxes beyond the entity box that give hits a part and its damage
 * factor, block other entities, and carry the reactive armor tiles. Each vehicle has its own copies,
 * made from the definition, because the tiles and the box positions change.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL)
public final class PartBoxesComponent implements VehicleComponent {

    private static final PartBoundingBox[] NO_BOXES = new PartBoundingBox[0];

    private final ExtraBoundingBoxTracker tracker;
    private final ReactiveArmor reactiveArmor;
    private PartBoundingBox[] boxes = NO_BOXES;
    /** The world box around every part box; null until asked for after the boxes last moved. */
    @Nullable
    private AABB envelope;

    public PartBoxesComponent(AircraftEntity vehicle, SyncedValue<String> eraState) {
        this.tracker = new ExtraBoundingBoxTracker(vehicle);
        this.reactiveArmor = new ReactiveArmor(this, eraState);
    }

    public PartBoundingBox[] boxes() {
        return this.boxes;
    }

    /**
     * The smallest world box around the boxes of every part (popped armour tiles included), or null when
     * there are none. An area that misses it touches no part, which spares the per-part tests of the
     * many entity searches that pass near a vehicle.
     */
    @Nullable
    public AABB envelope() {
        if (this.envelope == null && this.boxes.length > 0) {
            AABB union = this.boxes[0].boundingBox();
            for (int i = 1; i < this.boxes.length; i++) {
                union = union.minmax(this.boxes[i].boundingBox());
            }
            this.envelope = union;
        }
        return this.envelope;
    }

    private void partMoved() {
        this.envelope = null;
    }

    public ReactiveArmor reactiveArmor() {
        return this.reactiveArmor;
    }

    /** Fresh copies of the definition's boxes, all tiles intact. */
    public void replaceFrom(VehicleSpec info) {
        this.boxes = PartBoundingBox.copyAll(info.hitBoxes().boxes());
        for (PartBoundingBox box : this.boxes) {
            box.setMovedListener(this::partMoved);
        }
        this.envelope = null;
        this.tracker.invalidate();
        this.reactiveArmor.onBoxesReplaced();
    }

    /** Part boxes for entity collision; the array is reused, so callers must not keep it. */
    public AABB[] collisionBoxes() {
        return this.tracker.getCollisionBoxes();
    }

    /** The boxes follow the hull after the physics step. */
    @Override
    public void serverTick(TickContext ctx) {
        this.tracker.tick(false);
    }

    /** The client also carries its own player along when it stands on a moving box. */
    @Override
    public void clientTick(TickContext ctx) {
        this.tracker.tick(true);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString(VehicleNbtCodec.ERA_STATE, this.reactiveArmor.buildState());
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(VehicleNbtCodec.ERA_STATE)) {
            this.reactiveArmor.applyState(tag.getString(VehicleNbtCodec.ERA_STATE));
            this.reactiveArmor.syncToClients(true);
        }
    }
}
