package com.ivomens.mcheli.core.math;

/**
 * A point or offset on a plane, such as a radar contact relative to the vehicle (x east, y south).
 *
 * @param x the first coordinate
 * @param y the second coordinate
 */
public record Vec2d(double x, double y) {
}
