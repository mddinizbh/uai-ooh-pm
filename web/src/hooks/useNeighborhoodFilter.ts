import { useState, useEffect, useCallback } from 'react';
import { busLinesApi, ApiClientError } from '../api/client';
import type { NeighborhoodSummary, NeighborhoodLines } from '../api/types';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface NeighborhoodFilterState {
  /** Full list of all neighbourhoods (loaded once on mount) */
  neighborhoods: NeighborhoodSummary[];
  /** Neighbourhoods filtered by the current search query */
  filteredNeighborhoods: NeighborhoodSummary[];
  /** ID of the currently selected neighbourhood, or null */
  selectedNeighborhoodId: number | null;
  /** Grouped lines for the selected neighbourhood, or null if none selected */
  neighborhoodLines: NeighborhoodLines | null;
  /** Text in the neighbourhood search / autocomplete field */
  searchQuery: string;
  /** True while the initial neighbourhood list is loading */
  isLoadingNeighborhoods: boolean;
  /** True while the lines for the selected neighbourhood are loading */
  isLoadingLines: boolean;
  /** Error message, or null */
  error: string | null;
  /** Select a neighbourhood by its id — fetches its lines */
  selectNeighborhood: (id: number) => void;
  /** Clear the selected neighbourhood (preserves selected lines on the map) */
  clearFilter: () => void;
  /** Update the search / autocomplete text */
  setSearchQuery: (q: string) => void;
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export function useNeighborhoodFilter(): NeighborhoodFilterState {
  const [neighborhoods, setNeighborhoods] = useState<NeighborhoodSummary[]>([]);
  const [selectedNeighborhoodId, setSelectedNeighborhoodId] = useState<number | null>(null);
  const [neighborhoodLines, setNeighborhoodLines] = useState<NeighborhoodLines | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoadingNeighborhoods, setIsLoadingNeighborhoods] = useState(true);
  const [isLoadingLines, setIsLoadingLines] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ── Load neighbourhood list on mount ──────────────────────────────────────

  useEffect(() => {
    let cancelled = false;
    setIsLoadingNeighborhoods(true);
    busLinesApi
      .listNeighborhoods()
      .then((list) => {
        if (!cancelled) {
          setNeighborhoods(list);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          const msg =
            err instanceof ApiClientError
              ? err.message
              : 'Erro ao carregar bairros. Tente novamente.';
          setError(msg);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingNeighborhoods(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // ── Filter neighbourhoods by search query ─────────────────────────────────

  const filteredNeighborhoods =
    searchQuery.trim() === ''
      ? neighborhoods
      : neighborhoods.filter((n) =>
          n.name.toLowerCase().includes(searchQuery.toLowerCase()),
        );

  // ── Select a neighbourhood ────────────────────────────────────────────────

  const selectNeighborhood = useCallback(
    (id: number) => {
      // If already selected, no-op (idempotent)
      if (id === selectedNeighborhoodId) return;

      const found = neighborhoods.find((n) => n.id === id);
      if (found) {
        setSearchQuery(found.name);
      }

      setSelectedNeighborhoodId(id);
      setNeighborhoodLines(null);
      setIsLoadingLines(true);
      setError(null);

      busLinesApi
        .neighborhoodLines(id)
        .then((lines) => {
          setNeighborhoodLines(lines);
        })
        .catch((err) => {
          const msg =
            err instanceof ApiClientError
              ? err.message
              : 'Erro ao carregar linhas do bairro.';
          setError(msg);
          setSelectedNeighborhoodId(null);
        })
        .finally(() => {
          setIsLoadingLines(false);
        });
    },
    [selectedNeighborhoodId, neighborhoods],
  );

  // ── Clear filter ──────────────────────────────────────────────────────────

  const clearFilter = useCallback(() => {
    setSelectedNeighborhoodId(null);
    setNeighborhoodLines(null);
    setSearchQuery('');
    setError(null);
  }, []);

  return {
    neighborhoods,
    filteredNeighborhoods,
    selectedNeighborhoodId,
    neighborhoodLines,
    searchQuery,
    isLoadingNeighborhoods,
    isLoadingLines,
    error,
    selectNeighborhood,
    clearFilter,
    setSearchQuery,
  };
}
