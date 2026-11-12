package com.ivomens.mcheli.client.model;

/** A vertex position or normal of a parsed model, in model units. Only used while a model loads. */
public record ModelVertex(float x, float y, float z) {

    public ModelVertex(float x, float y) {
        this(x, y, 0.0F);
    }

    /** This vector scaled to length one; the zero vector stays as it is. */
    public ModelVertex normalized() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        if (d == 0.0) {
            return this;
        }
        return new ModelVertex((float) (this.x / d), (float) (this.y / d), (float) (this.z / d));
    }

    public ModelVertex plus(ModelVertex v) {
        return new ModelVertex(this.x + v.x, this.y + v.y, this.z + v.z);
    }

    /** The dot product with {@code v}, in float precision. */
    public float dot(ModelVertex v) {
        return this.x * v.x + this.y * v.y + this.z * v.z;
    }
}
