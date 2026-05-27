package com.uai.buslines.application;

import com.uai.buslines.domain.model.BoundaryParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link CrsReprojector}.
 */
class CrsReprojectorTest {

    @Test
    void reprojectsUtmZone23SouthToWgs84NearBeloHorizonte() {
        // EPSG:32723 — WGS84 / UTM zone 23S. A point in central BH.
        double[] lonLat = CrsReprojector.forEpsg(32723).toWgs84(612191.10, 7801958.38);

        assertThat(lonLat[0]).isCloseTo(-43.93, within(0.05));   // lon
        assertThat(lonLat[1]).isCloseTo(-19.87, within(0.05));   // lat
    }

    @Test
    void identityPassesCoordinatesThroughUnchanged() {
        CrsReprojector identity = CrsReprojector.identity();

        assertThat(identity.isIdentity()).isTrue();
        assertThat(identity.toWgs84(-43.9, -19.9)).containsExactly(-43.9, -19.9);
    }

    @Test
    void epsg4326IsTreatedAsIdentity() {
        assertThat(CrsReprojector.forEpsg(4326).isIdentity()).isTrue();
    }

    @Test
    void unsupportedCrsThrows() {
        // EPSG:31983 — SIRGAS 2000 / UTM 23S, not a WGS84/UTM zone → unsupported.
        assertThatThrownBy(() -> CrsReprojector.forEpsg(31983))
                .isInstanceOf(BoundaryParseException.class)
                .hasMessageContaining("31983");
    }
}
