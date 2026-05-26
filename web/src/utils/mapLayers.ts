import type { Map as MapLibreMap } from 'maplibre-gl';
import type { FeatureCollection, Feature, Geometry, GeoJSON } from 'geojson';
import type { LineDetail } from '../api/types';

// ── Visual style constants ────────────────────────────────────────────────────

/** Width (pixels) for the currently active (focused) line */
const ACTIVE_LINE_WIDTH = 5;
/** Width (pixels) for other selected lines */
const DIMMED_LINE_WIDTH = 3;
/** Opacity for the active line */
const ACTIVE_LINE_OPACITY = 1.0;
/** Opacity for other selected (non-active) lines */
const DIMMED_LINE_OPACITY = 0.55;

/** Palette used for selected lines — cycles if > 10 lines selected */
const LINE_COLORS = [
  '#e74c3c', // red
  '#3498db', // blue
  '#2ecc71', // green
  '#f39c12', // orange
  '#9b59b6', // purple
  '#1abc9c', // teal
  '#e67e22', // dark-orange
  '#34495e', // navy
  '#e91e63', // pink
  '#00bcd4', // cyan
];

export function lineColor(colorIndex: number): string {
  return LINE_COLORS[colorIndex % LINE_COLORS.length];
}

// ── Source/layer ID helpers ───────────────────────────────────────────────────

export function sourceId(lineId: number): string {
  return `line-source-${lineId}`;
}

export function layerId(lineId: number): string {
  return `line-layer-${lineId}`;
}

// ── Core layer operations ─────────────────────────────────────────────────────

/**
 * Add a GeoJSON source + line layer for the given line.
 * All shapes (directions) are merged into one FeatureCollection.
 * A no-op if the source already exists.
 */
export function addLineLayer(
  map: MapLibreMap,
  lineId: number,
  detail: LineDetail,
  isActive: boolean,
  colorIndex: number,
): void {
  const sid = sourceId(lineId);
  const lid = layerId(lineId);

  if (map.getSource(sid)) return; // already rendered

  const features: Feature[] = detail.shapes
    .filter((s) => s.geometry != null)
    .map((s) => ({
      type: 'Feature' as const,
      geometry: s.geometry as Geometry,
      properties: { direction: s.direction },
    }));

  const data: FeatureCollection = { type: 'FeatureCollection', features };

  map.addSource(sid, { type: 'geojson', data });

  map.addLayer({
    id: lid,
    type: 'line',
    source: sid,
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': lineColor(colorIndex),
      'line-width': isActive ? ACTIVE_LINE_WIDTH : DIMMED_LINE_WIDTH,
      'line-opacity': isActive ? ACTIVE_LINE_OPACITY : DIMMED_LINE_OPACITY,
    },
  });
}

/**
 * Remove a line layer and its GeoJSON source from the map.
 * No-ops gracefully if layer/source does not exist.
 */
export function removeLineLayer(map: MapLibreMap, lineId: number): void {
  const lid = layerId(lineId);
  const sid = sourceId(lineId);

  if (map.getLayer(lid)) map.removeLayer(lid);
  if (map.getSource(sid)) map.removeSource(sid);
}

/**
 * Update the visual emphasis of an already-rendered line layer.
 * Switches between active (emphasized) and dimmed paint properties.
 */
export function updateLineEmphasis(
  map: MapLibreMap,
  lineId: number,
  isActive: boolean,
): void {
  const lid = layerId(lineId);
  if (!map.getLayer(lid)) return;

  map.setPaintProperty(
    lid,
    'line-opacity',
    isActive ? ACTIVE_LINE_OPACITY : DIMMED_LINE_OPACITY,
  );
  map.setPaintProperty(
    lid,
    'line-width',
    isActive ? ACTIVE_LINE_WIDTH : DIMMED_LINE_WIDTH,
  );
}

// ── Neighbourhood boundary layer ──────────────────────────────────────────────

/** Fixed source/layer IDs for the neighbourhood boundary — only one at a time. */
export const BOUNDARY_SOURCE_ID = 'neighborhood-boundary-source';
export const BOUNDARY_FILL_LAYER_ID = 'neighborhood-boundary-fill';
export const BOUNDARY_OUTLINE_LAYER_ID = 'neighborhood-boundary-outline';

/**
 * Add or replace the neighbourhood boundary highlight on the map.
 * Renders a translucent fill + visible outline (Polygon or MultiPolygon).
 */
export function addBoundaryLayer(map: MapLibreMap, geometry: Geometry): void {
  // Remove any existing boundary first (clean swap)
  removeBoundaryLayer(map);

  const data: GeoJSON = {
    type: 'Feature',
    geometry,
    properties: {},
  };

  map.addSource(BOUNDARY_SOURCE_ID, { type: 'geojson', data });

  // Translucent fill
  map.addLayer({
    id: BOUNDARY_FILL_LAYER_ID,
    type: 'fill',
    source: BOUNDARY_SOURCE_ID,
    paint: {
      'fill-color': '#4299e1',
      'fill-opacity': 0.12,
    },
  });

  // Solid outline
  map.addLayer({
    id: BOUNDARY_OUTLINE_LAYER_ID,
    type: 'line',
    source: BOUNDARY_SOURCE_ID,
    paint: {
      'line-color': '#2b6cb0',
      'line-width': 2,
      'line-opacity': 0.8,
    },
  });
}

/**
 * Remove the neighbourhood boundary highlight from the map.
 * No-ops if boundary layers/source do not exist.
 */
export function removeBoundaryLayer(map: MapLibreMap): void {
  if (map.getLayer(BOUNDARY_FILL_LAYER_ID)) map.removeLayer(BOUNDARY_FILL_LAYER_ID);
  if (map.getLayer(BOUNDARY_OUTLINE_LAYER_ID)) map.removeLayer(BOUNDARY_OUTLINE_LAYER_ID);
  if (map.getSource(BOUNDARY_SOURCE_ID)) map.removeSource(BOUNDARY_SOURCE_ID);
}
