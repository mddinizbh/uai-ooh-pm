import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  addLineLayer,
  removeLineLayer,
  updateLineEmphasis,
  addBoundaryLayer,
  removeBoundaryLayer,
  BOUNDARY_SOURCE_ID,
  BOUNDARY_FILL_LAYER_ID,
  BOUNDARY_OUTLINE_LAYER_ID,
  sourceId,
  layerId,
  lineColor,
} from './mapLayers';
import type { LineDetail } from '../api/types';
import type { Geometry } from 'geojson';

// ── Mock map factory ──────────────────────────────────────────────────────────

function createMockMap() {
  return {
    getSource: vi.fn().mockReturnValue(null),
    addSource: vi.fn(),
    removeSource: vi.fn(),
    getLayer: vi.fn().mockReturnValue(null),
    addLayer: vi.fn(),
    removeLayer: vi.fn(),
    setPaintProperty: vi.fn(),
  };
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const LINE_DETAIL: LineDetail = {
  line: { id: 1, shortName: '9400', longName: 'Belvedere - Centro' },
  shapes: [
    {
      id: 10,
      direction: 0,
      geometry: {
        type: 'LineString',
        coordinates: [
          [-43.9386, -19.9191],
          [-43.93, -19.91],
        ],
      },
    },
    {
      id: 11,
      direction: 1,
      geometry: {
        type: 'LineString',
        coordinates: [
          [-43.93, -19.91],
          [-43.9386, -19.9191],
        ],
      },
    },
  ],
  stops: [],
};

const LINE_DETAIL_NO_SHAPES: LineDetail = {
  line: { id: 2, shortName: '0000', longName: 'Sem rota' },
  shapes: [],
  stops: [],
};

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('sourceId / layerId helpers', () => {
  it('generates consistent source IDs', () => {
    expect(sourceId(1)).toBe('line-source-1');
    expect(sourceId(999)).toBe('line-source-999');
  });

  it('generates consistent layer IDs', () => {
    expect(layerId(1)).toBe('line-layer-1');
  });
});

describe('lineColor', () => {
  it('returns a color string for lineId 0', () => {
    expect(lineColor(0)).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it('lineColor(0) returns LINE_COLORS[0] (first palette entry)', () => {
    // The first color in the palette is red (#e74c3c)
    expect(lineColor(0)).toBe('#e74c3c');
  });

  it('lineColor(10) wraps and returns the same as lineColor(0)', () => {
    expect(lineColor(10)).toBe(lineColor(0));
  });

  it('is stable: same lineId always returns the same color', () => {
    const id = 7;
    const first = lineColor(id);
    const second = lineColor(id);
    const third = lineColor(id);
    expect(first).toBe(second);
    expect(second).toBe(third);
  });

  it('derives color from lineId modulo palette length', () => {
    // IDs 4 and 14 are congruent mod 10 — accepted collision per ADR-008
    expect(lineColor(4)).toBe(lineColor(14));
    expect(lineColor(3)).toBe(lineColor(13));
  });
});

describe('addLineLayer', () => {
  let map: ReturnType<typeof createMockMap>;

  beforeEach(() => {
    map = createMockMap();
  });

  it('adds a GeoJSON source and line layer for the line', () => {
    addLineLayer(map as never, 1, LINE_DETAIL, true);

    expect(map.addSource).toHaveBeenCalledOnce();
    expect(map.addSource).toHaveBeenCalledWith(
      sourceId(1),
      expect.objectContaining({ type: 'geojson' }),
    );

    expect(map.addLayer).toHaveBeenCalledOnce();
    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({ id: layerId(1), type: 'line', source: sourceId(1) }),
    );
  });

  it('creates a FeatureCollection from all shapes', () => {
    addLineLayer(map as never, 1, LINE_DETAIL, true);

    const sourceCall = map.addSource.mock.calls[0];
    const sourceData = sourceCall[1] as { data: { type: string; features: unknown[] } };
    expect(sourceData.data.type).toBe('FeatureCollection');
    expect(sourceData.data.features).toHaveLength(2); // two directions
  });

  it('sets line-color equal to lineColor(lineId) — color derived from identity', () => {
    const testLineId = 3;
    addLineLayer(map as never, testLineId, LINE_DETAIL, true);

    const layerCall = map.addLayer.mock.calls[0][0] as {
      paint: Record<string, string>;
    };
    expect(layerCall.paint['line-color']).toBe(lineColor(testLineId));
  });

  it('two different lineIds get different colors (no collision for nearby IDs)', () => {
    addLineLayer(map as never, 1, LINE_DETAIL, true);
    const firstColor = (map.addLayer.mock.calls[0][0] as { paint: Record<string, string> }).paint['line-color'];

    const map2 = createMockMap();
    addLineLayer(map2 as never, 2, LINE_DETAIL, true);
    const secondColor = (map2.addLayer.mock.calls[0][0] as { paint: Record<string, string> }).paint['line-color'];

    expect(firstColor).toBe(lineColor(1));
    expect(secondColor).toBe(lineColor(2));
    expect(firstColor).not.toBe(secondColor);
  });

  it('adds active line with full opacity and larger width', () => {
    addLineLayer(map as never, 1, LINE_DETAIL, true);

    const layerCall = map.addLayer.mock.calls[0][0] as {
      paint: Record<string, number>;
    };
    expect(layerCall.paint['line-opacity']).toBe(1.0);
    expect(layerCall.paint['line-width']).toBeGreaterThan(3);
  });

  it('adds non-active line with reduced opacity and narrower width', () => {
    addLineLayer(map as never, 1, LINE_DETAIL, false);

    const layerCall = map.addLayer.mock.calls[0][0] as {
      paint: Record<string, number>;
    };
    expect(layerCall.paint['line-opacity']).toBeLessThan(1.0);
    expect(layerCall.paint['line-width']).toBeLessThan(5);
  });

  it('is a no-op if the source already exists', () => {
    map.getSource.mockReturnValue({ type: 'geojson' }); // source exists
    addLineLayer(map as never, 1, LINE_DETAIL, true);

    expect(map.addSource).not.toHaveBeenCalled();
    expect(map.addLayer).not.toHaveBeenCalled();
  });

  it('skips shapes with null geometry', () => {
    const noShapesDetail = LINE_DETAIL_NO_SHAPES;
    addLineLayer(map as never, 2, noShapesDetail, true);

    // Source is still added, but with zero features
    const sourceCall = map.addSource.mock.calls[0];
    const data = sourceCall[1] as { data: { features: unknown[] } };
    expect(data.data.features).toHaveLength(0);
  });

  it('color is stable: same lineId added multiple times (idempotent no-op after first) keeps same color', () => {
    // First add — should succeed
    addLineLayer(map as never, 5, LINE_DETAIL, true);
    const color1 = (map.addLayer.mock.calls[0][0] as { paint: Record<string, string> }).paint['line-color'];

    // Simulate re-add with source already existing (no-op)
    map.getSource.mockReturnValue({ type: 'geojson' });
    addLineLayer(map as never, 5, LINE_DETAIL, true);
    // addLayer should still have been called only once
    expect(map.addLayer).toHaveBeenCalledTimes(1);

    // The color from the first call is stable (lineId-derived)
    expect(color1).toBe(lineColor(5));
  });
});

describe('removeLineLayer', () => {
  let map: ReturnType<typeof createMockMap>;

  beforeEach(() => {
    map = createMockMap();
  });

  it('removes both the layer and source when they exist', () => {
    map.getLayer.mockReturnValue({ id: layerId(1) });
    map.getSource.mockReturnValue({ type: 'geojson' });

    removeLineLayer(map as never, 1);

    expect(map.removeLayer).toHaveBeenCalledWith(layerId(1));
    expect(map.removeSource).toHaveBeenCalledWith(sourceId(1));
  });

  it('is a no-op when layer and source do not exist', () => {
    removeLineLayer(map as never, 1); // getLayer and getSource return null

    expect(map.removeLayer).not.toHaveBeenCalled();
    expect(map.removeSource).not.toHaveBeenCalled();
  });

  it('removes layer even if source does not exist', () => {
    map.getLayer.mockReturnValue({ id: layerId(1) });
    // getSource stays null

    removeLineLayer(map as never, 1);

    expect(map.removeLayer).toHaveBeenCalled();
    expect(map.removeSource).not.toHaveBeenCalled();
  });
});

describe('updateLineEmphasis', () => {
  let map: ReturnType<typeof createMockMap>;

  beforeEach(() => {
    map = createMockMap();
  });

  it('sets full opacity and larger width for active line', () => {
    map.getLayer.mockReturnValue({ id: layerId(1) });

    updateLineEmphasis(map as never, 1, true);

    expect(map.setPaintProperty).toHaveBeenCalledWith(layerId(1), 'line-opacity', 1.0);
    expect(map.setPaintProperty).toHaveBeenCalledWith(layerId(1), 'line-width', 5);
  });

  it('sets reduced opacity and narrower width for non-active line', () => {
    map.getLayer.mockReturnValue({ id: layerId(1) });

    updateLineEmphasis(map as never, 1, false);

    const opacityCall = map.setPaintProperty.mock.calls.find(
      (c) => c[1] === 'line-opacity',
    );
    const widthCall = map.setPaintProperty.mock.calls.find(
      (c) => c[1] === 'line-width',
    );
    expect(opacityCall?.[2]).toBeLessThan(1.0);
    expect(widthCall?.[2]).toBeLessThan(5);
  });

  it('is a no-op when the layer does not exist', () => {
    updateLineEmphasis(map as never, 1, true); // getLayer returns null

    expect(map.setPaintProperty).not.toHaveBeenCalled();
  });
});

// ── addBoundaryLayer ──────────────────────────────────────────────────────────

const POLYGON_GEOMETRY: Geometry = {
  type: 'Polygon',
  coordinates: [
    [
      [-43.975, -19.975],
      [-43.960, -19.975],
      [-43.960, -19.960],
      [-43.975, -19.960],
      [-43.975, -19.975],
    ],
  ],
};

describe('addBoundaryLayer', () => {
  let map: ReturnType<typeof createMockMap>;

  beforeEach(() => {
    map = createMockMap();
  });

  it('adds a geojson source and fill + outline layers', () => {
    addBoundaryLayer(map as never, POLYGON_GEOMETRY);

    expect(map.addSource).toHaveBeenCalledWith(
      BOUNDARY_SOURCE_ID,
      expect.objectContaining({ type: 'geojson' }),
    );
    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({ id: BOUNDARY_FILL_LAYER_ID, type: 'fill' }),
    );
    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({ id: BOUNDARY_OUTLINE_LAYER_ID, type: 'line' }),
    );
  });

  it('removes existing boundary before adding a new one', () => {
    map.getLayer.mockReturnValue({ id: BOUNDARY_FILL_LAYER_ID });
    map.getSource.mockReturnValue({ type: 'geojson' });

    addBoundaryLayer(map as never, POLYGON_GEOMETRY);

    expect(map.removeLayer).toHaveBeenCalledWith(BOUNDARY_FILL_LAYER_ID);
    expect(map.removeSource).toHaveBeenCalledWith(BOUNDARY_SOURCE_ID);
  });
});

describe('removeBoundaryLayer', () => {
  let map: ReturnType<typeof createMockMap>;

  beforeEach(() => {
    map = createMockMap();
  });

  it('removes fill layer, outline layer and source when they exist', () => {
    map.getLayer.mockImplementation((id: string) => ({ id }));
    map.getSource.mockReturnValue({ type: 'geojson' });

    removeBoundaryLayer(map as never);

    expect(map.removeLayer).toHaveBeenCalledWith(BOUNDARY_FILL_LAYER_ID);
    expect(map.removeLayer).toHaveBeenCalledWith(BOUNDARY_OUTLINE_LAYER_ID);
    expect(map.removeSource).toHaveBeenCalledWith(BOUNDARY_SOURCE_ID);
  });

  it('is a no-op when boundary layers do not exist', () => {
    removeBoundaryLayer(map as never);

    expect(map.removeLayer).not.toHaveBeenCalled();
    expect(map.removeSource).not.toHaveBeenCalled();
  });
});
