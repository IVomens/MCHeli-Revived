package com.ivomens.mcheli.vehicle.client.render.flywheel;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts what the Flywheel backend asks Flywheel to do: visuals, models and meshes made, instances
 * created, deleted and rewritten, and the vertices Flywheel copied out of our meshes (it copies every
 * mesh of its pool each time a mesh comes or goes). In a steady scene only the rewrites of moving parts
 * should grow; anything else growing frame after frame is churn that costs uploads on the GPU. The
 * counters are cheap enough to stay on; the client benchmark and the development checks read them.
 * It holds no Flywheel classes, so it can be read whether Flywheel is installed or not.
 */
public final class FlywheelVehicleStats {

    private static final LongAdder FRAMES = new LongAdder();
    private static final LongAdder VISUALS_CREATED = new LongAdder();
    private static final LongAdder VISUALS_DELETED = new LongAdder();
    private static final LongAdder MESHES_CREATED = new LongAdder();
    private static final LongAdder MODELS_CREATED = new LongAdder();
    private static final LongAdder KEEPERS_CREATED = new LongAdder();
    private static final LongAdder INSTANCES_CREATED = new LongAdder();
    private static final LongAdder INSTANCES_DELETED = new LongAdder();
    private static final LongAdder INSTANCE_WRITES = new LongAdder();
    private static final LongAdder MESH_WRITES = new LongAdder();
    private static final LongAdder MESH_VERTICES_WRITTEN = new LongAdder();
    private static final LongAdder WRITE_SPANS = new LongAdder();
    private static final LongAdder ORIGIN_CHANGES = new LongAdder();
    /**
     * Bytes Flywheel uploads per rewritten instance: a {@code TransformedInstance} is 76 bytes (colour,
     * overlay, light and a 4x4 matrix), which the instancing backend pads to 80.
     */
    public static final int INSTANCE_BYTES = 80;
    /** Bytes per vertex of Flywheel's mesh pool, which it uploads whole whenever a mesh comes or goes. */
    public static final int MESH_VERTEX_BYTES = 36;

    private FlywheelVehicleStats() {
    }

    static void frame() {
        FRAMES.increment();
    }

    static void visualCreated() {
        VISUALS_CREATED.increment();
    }

    static void visualDeleted() {
        VISUALS_DELETED.increment();
    }

    static void meshCreated() {
        MESHES_CREATED.increment();
    }

    static void modelCreated() {
        MODELS_CREATED.increment();
    }

    /** A model became resident: its keeper asked Flywheel for the instancer (making it if it was not there). */
    static void keeperCreated() {
        KEEPERS_CREATED.increment();
    }

    static void instancesCreated(int count) {
        INSTANCES_CREATED.add(count);
    }

    static void instancesDeleted(int count) {
        INSTANCES_DELETED.add(count);
    }

    static void instanceWritten() {
        INSTANCE_WRITES.increment();
    }

    /**
     * A run of rewritten instances that are next to each other in a vehicle's list for one model began.
     * The instancing backend uploads every run of changed instances with a call of its own, so this
     * estimates its buffer uploads.
     */
    static void writeSpan() {
        WRITE_SPANS.increment();
    }

    /** Flywheel moved its render origin, which clears every instancer: everything is made again. */
    static void originChanged() {
        ORIGIN_CHANGES.increment();
    }

    /** Flywheel copied a mesh into its mesh pool. */
    static void meshWritten(int vertices) {
        MESH_WRITES.increment();
        MESH_VERTICES_WRITTEN.add(vertices);
    }

    /** The counters since the game started. */
    public static Counts counts() {
        return new Counts(FRAMES.sum(), VISUALS_CREATED.sum(), VISUALS_DELETED.sum(), MESHES_CREATED.sum(),
                MODELS_CREATED.sum(), KEEPERS_CREATED.sum(), INSTANCES_CREATED.sum(), INSTANCES_DELETED.sum(),
                INSTANCE_WRITES.sum(), WRITE_SPANS.sum(), MESH_WRITES.sum(), MESH_VERTICES_WRITTEN.sum(),
                ORIGIN_CHANGES.sum());
    }

    /**
     * The counters at one moment. {@code frames} counts visual frames (one per vehicle and frame), the
     * rest what their names say.
     */
    public record Counts(long frames, long visualsCreated, long visualsDeleted, long meshesCreated,
                         long modelsCreated, long keepersCreated, long instancesCreated, long instancesDeleted,
                         long instanceWrites, long writeSpans, long meshWrites, long meshVerticesWritten,
                         long originChanges) {

        /** What happened between {@code earlier} and this. */
        public Counts since(Counts earlier) {
            return new Counts(this.frames - earlier.frames, this.visualsCreated - earlier.visualsCreated,
                    this.visualsDeleted - earlier.visualsDeleted, this.meshesCreated - earlier.meshesCreated,
                    this.modelsCreated - earlier.modelsCreated, this.keepersCreated - earlier.keepersCreated,
                    this.instancesCreated - earlier.instancesCreated, this.instancesDeleted - earlier.instancesDeleted,
                    this.instanceWrites - earlier.instanceWrites, this.writeSpans - earlier.writeSpans,
                    this.meshWrites - earlier.meshWrites, this.meshVerticesWritten - earlier.meshVerticesWritten,
                    this.originChanges - earlier.originChanges);
        }

        /**
         * Roughly the bytes these counts made Flywheel upload: the rewritten instances and the mesh pool
         * copies. An instancer that grows uploads all its instances again, which is not counted.
         */
        public long uploadBytes() {
            return this.instanceWrites * INSTANCE_BYTES + this.meshVerticesWritten * MESH_VERTEX_BYTES;
        }

        /** Whether nothing was made, created, deleted or uploaded: only instance rewrites may happen. */
        public boolean createsNothing() {
            return this.visualsCreated == 0 && this.visualsDeleted == 0 && this.meshesCreated == 0
                    && this.modelsCreated == 0 && this.keepersCreated == 0 && this.instancesCreated == 0
                    && this.instancesDeleted == 0 && this.meshWrites == 0 && this.originChanges == 0;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "visualFrames=%d visuals=+%d/-%d meshes=+%d models=+%d "
                            + "keepersCreated=+%d instances=+%d/-%d instanceWrites=%d (%d spans) "
                            + "meshUploads=%d (%d vertices) originChanges=%d uploadKB=%d",
                    this.frames, this.visualsCreated, this.visualsDeleted, this.meshesCreated, this.modelsCreated,
                    this.keepersCreated, this.instancesCreated, this.instancesDeleted, this.instanceWrites,
                    this.writeSpans, this.meshWrites, this.meshVerticesWritten, this.originChanges,
                    this.uploadBytes() / 1024);
        }
    }
}
