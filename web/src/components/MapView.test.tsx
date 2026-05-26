import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, act } from '@testing-library/react';
import { MapView } from './MapView';
import type { LineDetail } from '../api/types';

// Use the __mocks__/maplibre-gl.ts manual mock
vi.mock('maplibre-gl');
// Silence CSS import from maplibre-gl
vi.mock('maplibre-gl/dist/maplibre-gl.css', () => ({}));

import { Map as MockMap } from 'maplibre-gl';

// Type helper to access the mock-only `lastInstance` static property
// (the real maplibre-gl Map type does not expose this — it's mock-only)
type MockMapCtor = typeof MockMap & {
  lastInstance: {
    addSource: ReturnType<typeof vi.fn>;
    removeSource: ReturnType<typeof vi.fn>;
    getSource: ReturnType<typeof vi.fn>;
    addLayer: ReturnType<typeof vi.fn>;
    removeLayer: ReturnType<typeof vi.fn>;
    getLayer: ReturnType<typeof vi.fn>;
    setPaintProperty: ReturnType<typeof vi.fn>;
  };
};

const getMockMap = () => (MockMap as unknown as MockMapCtor).lastInstance;

// ── Fixtures ──────────────────────────────────────────────────────────────────

const makeDetail = (id: number): LineDetail => ({
  line: { id, shortName: `${id}00`, longName: `Linha ${id}00 - Centro` },
  shapes: [
    {
      id: id * 10,
      direction: 0,
      geometry: {
        type: 'LineString',
        coordinates: [[-43.9, -19.9], [-43.8, -19.8]],
      },
    },
  ],
  stops: [],
});

const DETAIL_1 = makeDetail(1);
const DETAIL_2 = makeDetail(2);

// ── Helper ────────────────────────────────────────────────────────────────────

function makeSelectedLines(...details: LineDetail[]): Map<number, LineDetail> {
  return new Map(details.map((d) => [d.line.id, d]));
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('MapView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the map container element', () => {
    const { container } = render(
      <MapView selectedLines={new Map()} activeLineId={null} />,
    );
    expect(container.querySelector('[aria-label]')).toBeTruthy();
  });

  it('adds source and layer when a line is selected', () => {
    const selected = makeSelectedLines(DETAIL_1);
    render(<MapView selectedLines={selected} activeLineId={1} />);

    const map = getMockMap();
    expect(map.addSource).toHaveBeenCalledWith(
      'line-source-1',
      expect.objectContaining({ type: 'geojson' }),
    );
    expect(map.addLayer).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'line-layer-1', type: 'line' }),
    );
  });

  it('does not add source/layer when no lines are selected', () => {
    render(<MapView selectedLines={new Map()} activeLineId={null} />);

    const map = getMockMap();
    expect(map.addSource).not.toHaveBeenCalled();
    expect(map.addLayer).not.toHaveBeenCalled();
  });

  it('removes layer and source when a line is deselected', () => {
    const selected = makeSelectedLines(DETAIL_1);
    const { rerender } = render(
      <MapView selectedLines={selected} activeLineId={1} />,
    );

    const map = getMockMap();
    // Simulate the layer existing in the map
    map.getLayer.mockReturnValue({ id: 'line-layer-1' });
    map.getSource.mockReturnValue({ type: 'geojson' });

    // Deselect the line
    act(() => {
      rerender(<MapView selectedLines={new Map()} activeLineId={null} />);
    });

    expect(map.removeLayer).toHaveBeenCalledWith('line-layer-1');
    expect(map.removeSource).toHaveBeenCalledWith('line-source-1');
  });

  describe('multi-line emphasis', () => {
    it('adds active line with full opacity', () => {
      const selected = makeSelectedLines(DETAIL_1, DETAIL_2);
      render(<MapView selectedLines={selected} activeLineId={1} />);

      const map = getMockMap();
      const line1LayerCall = map.addLayer.mock.calls.find(
        (c: unknown[]) => (c[0] as { id: string }).id === 'line-layer-1',
      );
      expect(line1LayerCall).toBeDefined();
      const paint = (line1LayerCall![0] as { paint: Record<string, number> }).paint;
      expect(paint['line-opacity']).toBe(1.0);
    });

    it('adds non-active line with reduced opacity (dimmed)', () => {
      const selected = makeSelectedLines(DETAIL_1, DETAIL_2);
      // Line 1 is active, line 2 should be dimmed
      render(<MapView selectedLines={selected} activeLineId={1} />);

      const map = getMockMap();
      const line2LayerCall = map.addLayer.mock.calls.find(
        (c: unknown[]) => (c[0] as { id: string }).id === 'line-layer-2',
      );
      expect(line2LayerCall).toBeDefined();
      const paint = (line2LayerCall![0] as { paint: Record<string, number> }).paint;
      expect(paint['line-opacity']).toBeLessThan(1.0);
    });

    it('updates emphasis via setPaintProperty when activeLineId changes', () => {
      const selected = makeSelectedLines(DETAIL_1, DETAIL_2);
      const { rerender } = render(
        <MapView selectedLines={selected} activeLineId={1} />,
      );

      const map = getMockMap();
      // Simulate layers exist
      map.getLayer.mockReturnValue({ id: 'exists' });
      vi.clearAllMocks();
      // Restore mocks after clearing
      map.getLayer.mockReturnValue({ id: 'exists' });

      act(() => {
        rerender(<MapView selectedLines={selected} activeLineId={2} />);
      });

      expect(map.setPaintProperty).toHaveBeenCalled();
    });
  });
});
