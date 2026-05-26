/**
 * Unit tests for useNeighborhoodFilter hook.
 */
import { describe, it, expect } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { useNeighborhoodFilter } from './useNeighborhoodFilter';
import {
  NEIGHBORHOOD_BELVEDERE,
  NEIGHBORHOOD_SAVASSI,
  NEIGHBORHOOD_BELVEDERE_LINES,
} from '../test/handlers';

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Waits until both loading is false AND neighbourhoods are populated. */
async function waitForNeighborhoodsLoaded(result: { current: ReturnType<typeof useNeighborhoodFilter> }) {
  await waitFor(() => {
    expect(result.current.isLoadingNeighborhoods).toBe(false);
    expect(result.current.neighborhoods.length).toBeGreaterThan(0);
  });
}

describe('useNeighborhoodFilter', () => {
  // ── Initial load ───────────────────────────────────────────────────────────

  it('loads neighbourhood list on mount', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());

    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    expect(result.current.neighborhoods).toHaveLength(2);
    expect(result.current.neighborhoods[0].name).toBe('Belvedere');
    expect(result.current.neighborhoods[1].name).toBe('Savassi');
  });

  it('starts with no selection', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());

    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    expect(result.current.selectedNeighborhoodId).toBeNull();
    expect(result.current.neighborhoodLines).toBeNull();
  });

  // ── Select neighbourhood ───────────────────────────────────────────────────

  it('selecting a neighbourhood fetches its lines', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(result);

    act(() => {
      result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id);
    });

    await waitFor(() => {
      expect(result.current.neighborhoodLines).not.toBeNull();
    });

    expect(result.current.selectedNeighborhoodId).toBe(NEIGHBORHOOD_BELVEDERE.id);
    expect(result.current.neighborhoodLines?.neighborhoodName).toBe('Belvedere');
    expect(result.current.neighborhoodLines?.passesThrough).toHaveLength(1);
    expect(result.current.neighborhoodLines?.departsFrom).toHaveLength(1);
    expect(result.current.neighborhoodLines?.arrivesAt).toHaveLength(0);
  });

  it('selecting a neighbourhood sets the search query to its name', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(result);

    act(() => {
      result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id);
    });

    await waitFor(() => {
      expect(result.current.searchQuery).toBe('Belvedere');
    });
  });

  it('selecting the same neighbourhood twice is a no-op (idempotent)', async () => {
    let callCount = 0;
    server.use(
      http.get('/api/neighborhoods/:id/lines', ({ params }) => {
        if (Number(params.id) === NEIGHBORHOOD_BELVEDERE.id) {
          callCount++;
          return HttpResponse.json(NEIGHBORHOOD_BELVEDERE_LINES);
        }
        return HttpResponse.json({ status: 404 }, { status: 404 });
      }),
    );

    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(result);

    act(() => { result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id); });
    await waitFor(() => {
      expect(result.current.neighborhoodLines).not.toBeNull();
    });

    act(() => { result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id); });

    // Only one fetch should have happened
    expect(callCount).toBe(1);
  });

  // ── Clear filter ───────────────────────────────────────────────────────────

  it('clearFilter resets selection to null without removing neighbourhood list', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(result);

    act(() => { result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id); });
    await waitFor(() => {
      expect(result.current.neighborhoodLines).not.toBeNull();
    });

    act(() => { result.current.clearFilter(); });

    expect(result.current.selectedNeighborhoodId).toBeNull();
    expect(result.current.neighborhoodLines).toBeNull();
    expect(result.current.searchQuery).toBe('');
    // The full neighbourhood list must still be present
    expect(result.current.neighborhoods).toHaveLength(2);
  });

  // ── Search query filter ────────────────────────────────────────────────────

  it('filteredNeighborhoods is the full list when searchQuery is empty', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    expect(result.current.filteredNeighborhoods).toHaveLength(2);
  });

  it('filteredNeighborhoods filters by name (case-insensitive)', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    act(() => { result.current.setSearchQuery('sav'); });

    expect(result.current.filteredNeighborhoods).toHaveLength(1);
    expect(result.current.filteredNeighborhoods[0].name).toBe('Savassi');
  });

  it('filteredNeighborhoods returns empty when no match', async () => {
    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    act(() => { result.current.setSearchQuery('XYZNOTEXIST'); });

    expect(result.current.filteredNeighborhoods).toHaveLength(0);
  });

  // ── Different inputs produce same selection state ──────────────────────────

  it('selecting by list click vs autocomplete produces the same state', async () => {
    const { result: r1 } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(r1);

    // Simulate selecting from the list
    act(() => { r1.current.selectNeighborhood(NEIGHBORHOOD_SAVASSI.id); });
    await waitFor(() => {
      expect(r1.current.neighborhoodLines).not.toBeNull();
    });
    const stateFromList = {
      id: r1.current.selectedNeighborhoodId,
      name: r1.current.neighborhoodLines?.neighborhoodName,
    };

    const { result: r2 } = renderHook(() => useNeighborhoodFilter());
    await waitForNeighborhoodsLoaded(r2);

    // Simulate selecting from autocomplete (same underlying selectNeighborhood call)
    act(() => { r2.current.setSearchQuery('Savassi'); });
    act(() => { r2.current.selectNeighborhood(NEIGHBORHOOD_SAVASSI.id); });
    await waitFor(() => {
      expect(r2.current.neighborhoodLines).not.toBeNull();
    });
    const stateFromSearch = {
      id: r2.current.selectedNeighborhoodId,
      name: r2.current.neighborhoodLines?.neighborhoodName,
    };

    expect(stateFromList).toEqual(stateFromSearch);
  });

  // ── Error handling ─────────────────────────────────────────────────────────

  it('shows error when neighbourhood lines API fails', async () => {
    server.use(
      http.get('/api/neighborhoods/:id/lines', () =>
        HttpResponse.json(
          { status: 500, message: 'Server error' },
          { status: 500 },
        ),
      ),
    );

    const { result } = renderHook(() => useNeighborhoodFilter());
    await waitFor(() => {
      expect(result.current.isLoadingNeighborhoods).toBe(false);
    });

    act(() => { result.current.selectNeighborhood(NEIGHBORHOOD_BELVEDERE.id); });

    await waitFor(() => {
      expect(result.current.error).not.toBeNull();
    });

    expect(result.current.selectedNeighborhoodId).toBeNull();
  });
});
