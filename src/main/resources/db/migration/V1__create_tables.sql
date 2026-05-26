-- ============================================================
-- V1: Initial schema — BH Bus Lines (uai_buslines database)
--
-- Rules enforced here:
--   • NO tenant_id column anywhere (ADR-004 — deliberate deviation from RULE-JAVA-03)
--   • Geometry stored as JSONB GeoJSON, NOT PostGIS types (ADR-003)
--   • dataset_version column ties each row to an import batch (supports atomic swap)
-- ============================================================

-- ── dataset_version ─────────────────────────────────────────────────────────
-- Tracks every import batch; the ACTIVE version is the one currently served.
-- Supports atomic dataset swap on reimport: insert new version rows, then flip status.
CREATE TABLE dataset_version (
    version          BIGSERIAL                NOT NULL,
    imported_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    gtfs_source_etag VARCHAR(255),
    status           VARCHAR(50)              NOT NULL,
    CONSTRAINT pk_dataset_version PRIMARY KEY (version),
    CONSTRAINT chk_dataset_version_status
        CHECK (status IN ('ACTIVE', 'IMPORTING', 'ARCHIVED', 'FAILED'))
);

-- ── neighborhood ─────────────────────────────────────────────────────────────
-- BH neighbourhoods with their GeoJSON boundaries (for map rendering only).
-- Boundary CRS must be WGS84 / EPSG:4326 — validated at import time.
CREATE TABLE neighborhood (
    id               BIGSERIAL    NOT NULL,
    name             VARCHAR(255) NOT NULL,
    boundary_geojson JSONB        NOT NULL,
    dataset_version  BIGINT       NOT NULL,
    CONSTRAINT pk_neighborhood PRIMARY KEY (id)
);

-- ── line ─────────────────────────────────────────────────────────────────────
-- A bus line (maps to a GTFS route).
CREATE TABLE line (
    id              BIGSERIAL    NOT NULL,
    short_name      VARCHAR(50)  NOT NULL,   -- e.g. "9400" (human-facing line number)
    long_name       VARCHAR(500) NOT NULL,
    gtfs_route_id   VARCHAR(255) NOT NULL,
    dataset_version BIGINT       NOT NULL,
    CONSTRAINT pk_line PRIMARY KEY (id)
);

-- ── line_shape ───────────────────────────────────────────────────────────────
-- Route geometry per direction (0 = outbound, 1 = inbound).
-- geometry_geojson is a GeoJSON LineString — used by the frontend for rendering.
-- NOT a PostGIS geometry column (ADR-003).
CREATE TABLE line_shape (
    id               BIGSERIAL NOT NULL,
    line_id          BIGINT    NOT NULL,
    direction        SMALLINT  NOT NULL,
    geometry_geojson JSONB     NOT NULL,
    dataset_version  BIGINT    NOT NULL,
    CONSTRAINT pk_line_shape     PRIMARY KEY (id),
    CONSTRAINT fk_line_shape_line FOREIGN KEY (line_id) REFERENCES line (id),
    CONSTRAINT chk_line_shape_direction CHECK (direction IN (0, 1))
);

-- ── stop ─────────────────────────────────────────────────────────────────────
-- Individual bus stops with WGS84 coordinates (lat/lon, not PostGIS point).
CREATE TABLE stop (
    id              BIGSERIAL        NOT NULL,
    gtfs_stop_id    VARCHAR(255)     NOT NULL,
    name            VARCHAR(255)     NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lon             DOUBLE PRECISION NOT NULL,
    dataset_version BIGINT           NOT NULL,
    CONSTRAINT pk_stop PRIMARY KEY (id)
);

-- ── line_neighborhood ────────────────────────────────────────────────────────
-- Precomputed classification: which lines serve which neighbourhoods, and how.
-- Populated at GTFS import time by JTS point-in-polygon analysis (ADR-003).
-- relation values: PASSES_THROUGH | DEPARTS_FROM | ARRIVES_AT
-- NO tenant_id (ADR-004).
CREATE TABLE line_neighborhood (
    line_id         BIGINT      NOT NULL,
    neighborhood_id BIGINT      NOT NULL,
    relation        VARCHAR(50) NOT NULL,
    dataset_version BIGINT      NOT NULL,
    CONSTRAINT pk_line_neighborhood
        PRIMARY KEY (line_id, neighborhood_id, relation),
    CONSTRAINT fk_line_neighborhood_line
        FOREIGN KEY (line_id) REFERENCES line (id),
    CONSTRAINT fk_line_neighborhood_neighborhood
        FOREIGN KEY (neighborhood_id) REFERENCES neighborhood (id),
    CONSTRAINT chk_line_neighborhood_relation
        CHECK (relation IN ('PASSES_THROUGH', 'DEPARTS_FROM', 'ARRIVES_AT'))
);

-- Indexes for the two core query patterns (task_06 Read API):
--   • GET /api/neighborhoods/{id}/lines   → filter by neighborhood + relation
--   • GET /api/lines/{id}                 → filter by line
CREATE INDEX idx_line_neighborhood_by_neighborhood ON line_neighborhood (neighborhood_id, relation);
CREATE INDEX idx_line_neighborhood_by_line         ON line_neighborhood (line_id);
