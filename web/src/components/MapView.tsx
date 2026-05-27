import { useRef, useEffect, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { Geometry } from 'geojson';
import type { LineDetail } from '../api/types';
import {
  addLineLayer,
  removeLineLayer,
  updateLineEmphasis,
  addBoundaryLayer,
  removeBoundaryLayer,
} from '../utils/mapLayers';

// ── BH coordinates ────────────────────────────────────────────────────────────
const BH_CENTER: [number, number] = [-43.9386, -19.9191];
const BH_ZOOM = 12;

/**
 * Default free map style — OpenFreeMap Liberty (no API key required).
 * Can be overridden via the VITE_MAP_STYLE env var.
 */
export const DEFAULT_MAP_STYLE: string =
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
  import.meta.env.VITE_MAP_STYLE ?? 'https://tiles.openfreemap.org/styles/liberty';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface MapViewProps {
  selectedLines: Map<number, LineDetail>;
  activeLineId: number | null;
  /** GeoJSON geometry of the selected neighbourhood boundary (for highlight) */
  neighborhoodBoundary?: Geometry | null;
  /** Override the map style URL (useful for tests) */
  mapStyle?: string;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function MapView({
  selectedLines,
  activeLineId,
  neighborhoodBoundary = null,
  mapStyle = DEFAULT_MAP_STYLE,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);

  /**
   * Track which line IDs currently have sources/layers on the map.
   * Plain ref (no state) — mutation does not trigger re-renders.
   */
  const renderedIdsRef = useRef<Set<number>>(new Set());

  // ── Map initialisation ────────────────────────────────────────────────────

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const map = new maplibregl.Map({
      container: containerRef.current,
      style: mapStyle,
      center: BH_CENTER,
      zoom: BH_ZOOM,
    });

    mapRef.current = map;

    map.on('load', () => {
      setMapLoaded(true);
    });

    return () => {
      map.remove();
      mapRef.current = null;
      renderedIdsRef.current = new Set();
      setMapLoaded(false);
    };
  }, [mapStyle]);

  // ── Sync selected lines → map layers ─────────────────────────────────────

  useEffect(() => {
    const map = mapRef.current;
    if (!mapLoaded || !map) return;

    const current = new Set(selectedLines.keys());
    const rendered = renderedIdsRef.current;

    // Remove deselected lines
    for (const id of [...rendered]) {
      if (!current.has(id)) {
        removeLineLayer(map, id);
        rendered.delete(id);
      }
    }

    // Add newly selected lines
    const entries = Array.from(selectedLines.entries());
    entries.forEach(([id, detail]) => {
      if (!rendered.has(id)) {
        addLineLayer(map, id, detail, id === activeLineId);
        rendered.add(id);
      }
    });
  }, [mapLoaded, selectedLines]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Update emphasis when active line changes ──────────────────────────────

  useEffect(() => {
    const map = mapRef.current;
    if (!mapLoaded || !map) return;

    for (const id of renderedIdsRef.current) {
      updateLineEmphasis(map, id, id === activeLineId);
    }
  }, [mapLoaded, activeLineId]);

  // ── Sync neighbourhood boundary ───────────────────────────────────────────

  useEffect(() => {
    const map = mapRef.current;
    if (!mapLoaded || !map) return;

    if (neighborhoodBoundary) {
      addBoundaryLayer(map, neighborhoodBoundary);
    } else {
      removeBoundaryLayer(map);
    }
  }, [mapLoaded, neighborhoodBoundary]);

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div
      ref={containerRef}
      role="region"
      aria-label="Mapa de linhas de ônibus de BH"
      style={{ width: '100%', height: '100%' }}
    />
  );
}
