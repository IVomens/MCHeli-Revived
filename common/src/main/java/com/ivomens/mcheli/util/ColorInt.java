package com.ivomens.mcheli.util;

public class ColorInt {

    public final int r;
    public final int g;
    public final int b;
    public final int a;

    public ColorInt(int color) {
        this(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF);
    }

    public ColorInt(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }
}
