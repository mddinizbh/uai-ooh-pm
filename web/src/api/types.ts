import type { Geometry } from 'geojson';

// ── Line types ────────────────────────────────────────────────────────────────

export interface LineSummary {
  id: number;
  shortName: string;
  longName: string;
}

export interface LineShape {
  id: number;
  direction: number;
  /** Parsed GeoJSON geometry (LineString) returned directly by the API */
  geometry: Geometry;
}

export interface Stop {
  id: number;
  name: string;
  lat: number;
  lon: number;
}

export interface LineDetail {
  line: LineSummary;
  shapes: LineShape[];
  stops: Stop[];
}

// ── Neighbourhood types ───────────────────────────────────────────────────────

export interface NeighborhoodSummary {
  id: number;
  name: string;
  passesThroughCount: number;
  departsFromCount: number;
  arrivesAtCount: number;
}

export interface NeighborhoodLines {
  neighborhoodId: number;
  neighborhoodName: string;
  /** Parsed GeoJSON geometry (Polygon or MultiPolygon) for map boundary highlight */
  boundaryGeoJson: Geometry;
  passesThrough: LineSummary[];
  departsFrom: LineSummary[];
  arrivesAt: LineSummary[];
}

// ── Meta ──────────────────────────────────────────────────────────────────────

export interface MetaInfo {
  /** ISO-8601 timestamp or null if no import has run */
  lastImportedAt: string | null;
  attribution: string;
}

// ── Error ─────────────────────────────────────────────────────────────────────

export interface ApiErrorBody {
  status: number;
  message: string;
  timestamp: string;
}
