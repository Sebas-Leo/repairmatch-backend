package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.service.ApproximateLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Privacy rule: technicians only ever see coordinates snapped to a ~550 m grid. */
class ApproximateLocationTest {

    @Test
    void snapsToTheNearestGridLine() {
        assertThat(ApproximateLocation.snap(-12.0364)).isEqualTo(-12.035);
        assertThat(ApproximateLocation.snap(-77.0428)).isEqualTo(-77.045);
        assertThat(ApproximateLocation.snap(10.0026)).isEqualTo(10.005);
        assertThat(ApproximateLocation.snap(10.0024)).isEqualTo(10.0);
    }

    @Test
    void resultIsAlwaysAMultipleOfTheGridWithNoFloatingPointNoise() {
        for (double v = -77.1; v < -76.9; v += 0.00137) {
            double snapped = ApproximateLocation.snap(v);
            double steps = snapped / ApproximateLocation.GRID_DEGREES;
            assertThat(Math.abs(steps - Math.round(steps))).isLessThan(1e-6);
            assertThat(snapped).isEqualTo(Math.round(snapped * 1000.0) / 1000.0);
            assertThat(Math.abs(snapped - v)).isLessThanOrEqualTo(ApproximateLocation.GRID_DEGREES / 2 + 1e-9);
        }
    }

    @Test
    void anOffGridInputIsNeverReturnedAsIs() {
        double[] offGrid = {-12.0464, -77.0428, 0.0012, 45.123456};
        for (double v : offGrid) {
            assertThat(ApproximateLocation.snap(v)).isNotEqualTo(v);
        }
    }

    @Test
    void sameCellGivesTheSamePointSoNearbyHomesCannotBeTold() {
        assertThat(ApproximateLocation.snap(-12.0361)).isEqualTo(ApproximateLocation.snap(-12.0374));
    }

    @Test
    void missingOrInvalidValuesStayNull() {
        assertThat(ApproximateLocation.snap(null)).isNull();
        assertThat(ApproximateLocation.snap(Double.NaN)).isNull();
        assertThat(ApproximateLocation.snap(Double.POSITIVE_INFINITY)).isNull();
    }
}
