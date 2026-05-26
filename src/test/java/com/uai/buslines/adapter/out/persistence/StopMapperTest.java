package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.GtfsStop;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test — verifies that {@link StopMapper} round-trips a
 * {@link GtfsStop} through the entity representation without data loss.
 */
class StopMapperTest {

    @Test
    void toEntity_setsAllFieldsCorrectly() {
        GtfsStop stop = new GtfsStop("ST001", "Ponto Centro", -19.9167, -43.9345);

        StopEntity entity = StopMapper.toEntity(stop, 5L);

        assertThat(entity.getGtfsStopId()).isEqualTo("ST001");
        assertThat(entity.getName()).isEqualTo("Ponto Centro");
        assertThat(entity.getLat()).isEqualTo(-19.9167);
        assertThat(entity.getLon()).isEqualTo(-43.9345);
        assertThat(entity.getDatasetVersion()).isEqualTo(5L);
        assertThat(entity.getId()).isNull();
    }

    @Test
    void toDomain_reconstructsOriginalStop() {
        GtfsStop original = new GtfsStop("ST002", "Ponto Bairro A", -19.8800, -43.9100);

        StopEntity entity = StopMapper.toEntity(original, 3L);
        GtfsStop restored = StopMapper.toDomain(entity);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void roundTrip_preservesWgs84Coordinates() {
        // Coordinates must survive the DB round-trip as exact doubles
        GtfsStop stop = new GtfsStop("ST003", "Ponto Bairro B", -19.9500, -44.0000);

        StopEntity entity = StopMapper.toEntity(stop, 1L);
        GtfsStop restored = StopMapper.toDomain(entity);

        assertThat(restored.lat()).isEqualTo(-19.9500);
        assertThat(restored.lon()).isEqualTo(-44.0000);
    }

    @Test
    void toEntity_differentVersionIds_produceDifferentVersionTags() {
        GtfsStop stop = new GtfsStop("ST001", "Centro", -19.9167, -43.9345);

        StopEntity v1 = StopMapper.toEntity(stop, 1L);
        StopEntity v2 = StopMapper.toEntity(stop, 2L);

        assertThat(v1.getDatasetVersion()).isEqualTo(1L);
        assertThat(v2.getDatasetVersion()).isEqualTo(2L);
    }
}
