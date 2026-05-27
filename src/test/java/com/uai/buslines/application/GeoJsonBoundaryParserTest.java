package com.uai.buslines.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Neighborhood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GeoJsonBoundaryParser}.
 *
 * <p>No Spring context — the parser is instantiated with a plain {@link ObjectMapper}.
 */
class GeoJsonBoundaryParserTest {

    private GeoJsonBoundaryParser parser;

    @BeforeEach
    void setUp() {
        parser = new GeoJsonBoundaryParser(new ObjectMapper());
    }

    // ── 3.1a Valid FeatureCollection parsing ─────────────────────────────────────

    @Test
    void parsesFeatureCollectionIntoExpectedNeighborhoods() {
        byte[] bytes = BoundaryFixtures.FEATURE_COLLECTION_JSON.getBytes(StandardCharsets.UTF_8);

        List<Neighborhood> result = parser.parse(bytes);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Neighborhood::name)
                .containsExactly("Centro", "Bairro A", "Bairro B");
    }

    @Test
    void parsedNeighborhoodContainsBoundaryGeoJson() {
        byte[] bytes = BoundaryFixtures.FEATURE_COLLECTION_JSON.getBytes(StandardCharsets.UTF_8);

        List<Neighborhood> result = parser.parse(bytes);

        assertThat(result.get(0).boundaryGeoJson()).contains("Polygon");
        assertThat(result.get(0).boundaryGeoJson()).contains("-43.9445");
    }

    @Test
    void extractsNameFromNOMEPropertyUpperCase() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"type\":\"Feature\",\"properties\":{\"NOME\":\"Savassi\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}]}";

        List<Neighborhood> result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Savassi");
    }

    @Test
    void extractsNameFromLowercaseNameProperty() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"type\":\"Feature\",\"properties\":{\"name\":\"Lourdes\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}]}";

        List<Neighborhood> result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.get(0).name()).isEqualTo("Lourdes");
    }

    @Test
    void fallsBackToPositionalLabelWhenNoKnownPropertyPresent() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"type\":\"Feature\",\"properties\":{\"BAIRRO_ID\":\"42\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}]}";

        List<Neighborhood> result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.get(0).name()).isEqualTo("neighborhood_0");
    }

    @Test
    void skipsFeatureWithNullGeometry() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"type\":\"Feature\",\"properties\":{\"NOME\":\"Total\"}," +
                "\"geometry\":null}," +
                "{\"type\":\"Feature\",\"properties\":{\"NOME\":\"Centro\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}" +
                "]}";

        List<Neighborhood> result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Centro");
    }

    // ── 3.1b CRS validation ──────────────────────────────────────────────────────

    @Test
    void acceptsFeatureCollectionWithoutCrsProperty() {
        // RFC 7946 GeoJSON has no CRS member; must be treated as WGS84
        byte[] bytes = BoundaryFixtures.FEATURE_COLLECTION_JSON.getBytes(StandardCharsets.UTF_8);
        assertThat(parser.parse(bytes)).isNotEmpty();
    }

    @Test
    void acceptsWgs84CrsPropertyUrn() {
        String json = "{\"type\":\"FeatureCollection\"," +
                "\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"urn:ogc:def:crs:OGC:1.3:CRS84\"}}," +
                "\"features\":[{\"type\":\"Feature\",\"properties\":{\"NOME\":\"A\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}]}";

        assertThat(parser.parse(json.getBytes(StandardCharsets.UTF_8))).hasSize(1);
    }

    @Test
    void acceptsEpsg4326CrsProperty() {
        String json = "{\"type\":\"FeatureCollection\"," +
                "\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}," +
                "\"features\":[{\"type\":\"Feature\",\"properties\":{\"NOME\":\"A\"}," +
                "\"geometry\":" + BoundaryFixtures.CENTRO_GEOJSON + "}]}";

        assertThat(parser.parse(json.getBytes(StandardCharsets.UTF_8))).hasSize(1);
    }

    @Test
    void nonWgs84CrsPropertyThrowsBoundaryParseException() {
        // SIRGAS 2000 / UTM zone 23S — common in Brazilian government GIS exports
        String json = "{\"type\":\"FeatureCollection\"," +
                "\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:31983\"}}," +
                "\"features\":[]}";

        assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BoundaryParseException.class)
                .hasMessageContaining("EPSG:31983")
                .hasMessageContaining("WGS84");
    }

    @Test
    void oldBrazilianCrsThrowsBoundaryParseException() {
        // Córrego Alegre — old Brazilian datum, still found in some PBH files
        String json = "{\"type\":\"FeatureCollection\"," +
                "\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4220\"}}," +
                "\"features\":[]}";

        assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BoundaryParseException.class);
    }

    // ── 3.1b' CRS reprojection (UTM → WGS84) ─────────────────────────────────────

    @Test
    void reprojectsUtm23SBoundaryToWgs84() {
        // crs EPSG:32723 (WGS84 / UTM zone 23S) with a polygon in projected metres
        // near Belo Horizonte. Must be reprojected to WGS84 lon/lat at parse time.
        String json = "{\"type\":\"FeatureCollection\"," +
                "\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"urn:ogc:def:crs:EPSG::32723\"}}," +
                "\"features\":[{\"type\":\"Feature\",\"properties\":{\"NOME\":\"Ipiranga\"}," +
                "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" +
                "[612191.10,7801958.38],[612300.00,7801958.38]," +
                "[612300.00,7802100.00],[612191.10,7801958.38]]]}}]}";

        List<Neighborhood> result = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(1);
        String geo = result.get(0).boundaryGeoJson();
        // Reprojected to BH WGS84 range; raw UTM eastings/northings must be gone.
        assertThat(geo).contains("-43.9").contains("-19.8");
        assertThat(geo).doesNotContain("612191").doesNotContain("7801958");
    }

    @Test
    void extractEpsgCodeParsesUrnAndShortForms() {
        assertThat(GeoJsonBoundaryParser.extractEpsgCode("urn:ogc:def:crs:EPSG::32723")).isEqualTo(32723);
        assertThat(GeoJsonBoundaryParser.extractEpsgCode("EPSG:4326")).isEqualTo(4326);
        assertThat(GeoJsonBoundaryParser.extractEpsgCode("CRS84")).isNull();
    }

    // ── 3.1c Structural error cases ──────────────────────────────────────────────

    @Test
    void invalidJsonThrowsBoundaryParseException() {
        assertThatThrownBy(() -> parser.parse("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BoundaryParseException.class);
    }

    @Test
    void wrongRootTypeThrowsBoundaryParseException() {
        String json = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":null}";

        assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BoundaryParseException.class)
                .hasMessageContaining("FeatureCollection");
    }

    @Test
    void unsupportedGeometryTypeThrowsBoundaryParseException() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":[" +
                "{\"type\":\"Feature\",\"properties\":{\"NOME\":\"Centro\"}," +
                "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-43.9,-19.9]}}]}";

        assertThatThrownBy(() -> parser.parse(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BoundaryParseException.class)
                .hasMessageContaining("Point");
    }

    // ── 3.1d isWgs84Crs helper ───────────────────────────────────────────────────

    @Test
    void isWgs84CrsRecognisesAllExpectedPatterns() {
        assertThat(parser.isWgs84Crs("EPSG:4326")).isTrue();
        assertThat(parser.isWgs84Crs("urn:ogc:def:crs:EPSG::4326")).isTrue();
        assertThat(parser.isWgs84Crs("urn:ogc:def:crs:OGC:1.3:CRS84")).isTrue();
        assertThat(parser.isWgs84Crs("WGS84")).isTrue();
        assertThat(parser.isWgs84Crs("WGS 84")).isTrue();
    }

    @Test
    void isWgs84CrsRejectsNonWgs84Strings() {
        assertThat(parser.isWgs84Crs("EPSG:31983")).isFalse();
        assertThat(parser.isWgs84Crs("EPSG:4220")).isFalse();
        assertThat(parser.isWgs84Crs("SIRGAS2000")).isFalse();
    }
}
