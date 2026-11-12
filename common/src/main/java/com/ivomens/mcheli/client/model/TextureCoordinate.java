package com.ivomens.mcheli.client.model;

public class TextureCoordinate {

    public final float u;
    public final float v;
    public final float w;

    public TextureCoordinate(float u, float v) {
        this(u, v, 0.0F);
    }

    public TextureCoordinate(float u, float v, float w) {
        this.u = u;
        this.v = v;
        this.w = w;
    }
}
