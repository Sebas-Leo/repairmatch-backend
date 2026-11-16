package com.repairmatch.repairmatch_backend.service;

/**
 * Privacy helper: snaps a real coordinate to a coarse grid so technicians can see roughly where
 * demand is without learning a client's home. The grid step of {@value #GRID_DEGREES} degrees is
 * about 550 m of latitude. The exact coordinates are only shared once a proposal is accepted.
 */
public final class ApproximateLocation {
    /** Grid step in degrees (~550 m of latitude). */
    public static final double GRID_DEGREES = 0.005;

    private ApproximateLocation() {
    }

    /** Returns the nearest grid line to {@code value}, or {@code null} when there is no value. */
    public static Double snap(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return null;
        double onGrid = Math.round(value / GRID_DEGREES) * GRID_DEGREES;
        // Multiples of 0.005 have at most 3 decimals; rounding removes floating point noise.
        return Math.round(onGrid * 1000.0) / 1000.0;
    }
}
