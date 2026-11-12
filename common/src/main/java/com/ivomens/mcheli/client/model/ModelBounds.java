package com.ivomens.mcheli.client.model;

/** The axis-aligned box around every vertex of a model, in model units. */
public record ModelBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    /** The smallest coordinate on any axis. */
    public float min() {
        return Math.min(this.minX, Math.min(this.minY, this.minZ));
    }

    /** The largest coordinate on any axis. */
    public float max() {
        return Math.max(this.maxX, Math.max(this.maxY, this.maxZ));
    }

    /** The edge of the cube from {@link #min} to {@link #max} on every axis. */
    public float size() {
        return this.max() - this.min();
    }

    public float sizeX() {
        return this.maxX - this.minX;
    }

    public float sizeY() {
        return this.maxY - this.minY;
    }

    public float sizeZ() {
        return this.maxZ - this.minZ;
    }
}
