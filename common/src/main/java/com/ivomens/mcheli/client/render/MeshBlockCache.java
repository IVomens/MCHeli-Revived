package com.ivomens.mcheli.client.render;

import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Finished vertex bytes of baked meshes that are drawn the same way frame after frame: the parts of
 * parked vehicles, which make up most of what a busy airfield draws. A block is the mesh written
 * through one {@link VertexTransform}; drawing it again is one memory copy, and when only the
 * translation changed (the camera moved), a pass that adds the new translation to the positions
 * kept from before it was added. Either way the bytes are the ones {@link VertexTransform#fill}
 * writes.
 * <p>
 * A mesh gets a block the second time it is drawn the same way, so a vehicle that moves or a rotor
 * that turns, drawn differently every frame, never pays for one. Blocks live in native memory up to {@link #BUDGET_BYTES};
 * when that is used up, a block only makes room by evicting one that has not been drawn for
 * {@link #STALE_NANOS}, so a scene bigger than the budget keeps what it has instead of churning.
 * Render thread only.
 */
final class MeshBlockCache {

    /** Native memory for blocks, plus their untranslated positions on the heap. */
    private static final long BUDGET_BYTES = 64L << 20;
    /** Smaller meshes cost less to write than to look up. */
    static final int MIN_VERTICES = 24;
    private static final long STALE_NANOS = 500_000_000L;
    /** Blocks not drawn for this long are freed, so the models of vehicles gone from view can go too. */
    private static final long EXPIRE_NANOS = 10_000_000_000L;
    /** How many draws pass between looks for expired blocks. */
    private static final int SWEEP_EVERY = 4096;
    private static final int MAX_CANDIDATES = 4096;
    private static final int BYTES_PER_VERTEX = VertexTransform.VERTEX_SIZE + 3 * Float.BYTES;

    /** The key looked up, refilled for every mesh drawn. */
    private final Key probe = new Key();
    private final LinkedHashMap<Key, Block> blocks = new LinkedHashMap<>(256, 0.75F, true);
    /** Meshes drawn once in a way that has no block yet. */
    private final Set<Key> candidates = new HashSet<>();
    private long bytes;
    private int drawsSinceSweep;

    /**
     * Writes the mesh to {@code pointer} from its block, making the block on its second draw in the
     * same way. Returns false, having written nothing, when the caller has to write it itself.
     */
    boolean write(long pointer, float[] data, int first, int count, VertexTransform transform) {
        long now = System.nanoTime();
        if (++this.drawsSinceSweep >= SWEEP_EVERY) {
            this.drawsSinceSweep = 0;
            this.evictOlderThan(now - EXPIRE_NANOS);
        }
        this.probe.set(data, first, count, transform);
        Block block = this.blocks.get(this.probe);
        if (block == null) {
            block = this.makeBlock(data, first, count, transform, now);
            if (block == null) {
                return false;
            }
        } else if (block.translatedX != Float.floatToRawIntBits(transform.translationX())
                || block.translatedY != Float.floatToRawIntBits(transform.translationY())
                || block.translatedZ != Float.floatToRawIntBits(transform.translationZ())) {
            transform.retranslate(block.address, block.untranslated, count);
            block.translatedFor(transform);
        }
        block.lastDrawn = now;
        MemoryUtil.memCopy(block.address, pointer, (long) count * VertexTransform.VERTEX_SIZE);
        return true;
    }

    private Block makeBlock(float[] data, int first, int count, VertexTransform transform, long now) {
        if (!this.candidates.remove(this.probe)) {
            if (this.candidates.size() >= MAX_CANDIDATES) {
                this.candidates.clear();
            }
            this.candidates.add(this.probe.copy());
            return null;
        }
        long size = (long) count * BYTES_PER_VERTEX;
        if (!this.makeRoom(size, now)) {
            return null;
        }
        long address = MemoryUtil.nmemAlloc((long) count * VertexTransform.VERTEX_SIZE);
        if (address == 0L) {
            return null;
        }
        Block block = new Block(address, new float[count * 3]);
        transform.fill(block.address, data, first, count, block.untranslated);
        block.translatedFor(transform);
        this.blocks.put(this.probe.copy(), block);
        this.bytes += size;
        return block;
    }

    private void evictOlderThan(long drawnBefore) {
        Iterator<Map.Entry<Key, Block>> leastRecent = this.blocks.entrySet().iterator();
        while (leastRecent.hasNext()) {
            Map.Entry<Key, Block> eldest = leastRecent.next();
            if (eldest.getValue().lastDrawn - drawnBefore >= 0) {
                return;
            }
            this.free(eldest);
            leastRecent.remove();
        }
    }

    private void free(Map.Entry<Key, Block> entry) {
        MemoryUtil.nmemFree(entry.getValue().address);
        this.bytes -= (long) entry.getKey().count * BYTES_PER_VERTEX;
    }

    /** Evicts blocks not drawn lately until {@code size} more bytes fit; false if they cannot. */
    private boolean makeRoom(long size, long now) {
        Iterator<Map.Entry<Key, Block>> leastRecent = this.blocks.entrySet().iterator();
        while (this.bytes + size > BUDGET_BYTES) {
            if (!leastRecent.hasNext()) {
                return false;
            }
            Map.Entry<Key, Block> eldest = leastRecent.next();
            if (now - eldest.getValue().lastDrawn < STALE_NANOS) {
                return false;
            }
            this.free(eldest);
            leastRecent.remove();
        }
        return true;
    }

    /** Frees every block. */
    void clear() {
        for (Block block : this.blocks.values()) {
            MemoryUtil.nmemFree(block.address);
        }
        this.blocks.clear();
        this.candidates.clear();
        this.bytes = 0;
    }

    /** A mesh's vertices ({@code data} by identity) and the linear part of a transform. */
    private static final class Key {
        private float[] data;
        private int first;
        private int count;
        private final int[] linear = new int[VertexTransform.KEY_INTS];
        private int hash;

        void set(float[] data, int first, int count, VertexTransform transform) {
            this.data = data;
            this.first = first;
            this.count = count;
            transform.linearKey(this.linear);
            this.hash = ((System.identityHashCode(data) * 31 + first) * 31 + count) * 31 + Arrays.hashCode(this.linear);
        }

        Key copy() {
            Key copy = new Key();
            copy.data = this.data;
            copy.first = this.first;
            copy.count = this.count;
            System.arraycopy(this.linear, 0, copy.linear, 0, this.linear.length);
            copy.hash = this.hash;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && other.data == this.data && other.first == this.first
                    && other.count == this.count && other.hash == this.hash && Arrays.equals(other.linear, this.linear);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    /** A mesh's finished vertices in native memory, and its positions before the translation. */
    private static final class Block {
        final long address;
        final float[] untranslated;
        int translatedX;
        int translatedY;
        int translatedZ;
        long lastDrawn;

        Block(long address, float[] untranslated) {
            this.address = address;
            this.untranslated = untranslated;
        }

        void translatedFor(VertexTransform transform) {
            this.translatedX = Float.floatToRawIntBits(transform.translationX());
            this.translatedY = Float.floatToRawIntBits(transform.translationY());
            this.translatedZ = Float.floatToRawIntBits(transform.translationZ());
        }
    }
}
