import { useEffect, useRef, useState } from 'react';
import { MapView } from './components/MapView';
import { SearchPanel } from './components/SearchPanel';
import { NeighborhoodPanel } from './components/NeighborhoodPanel';
import { Attribution } from './components/Attribution';
import { useSelectedLines } from './hooks/useSelectedLines';
import { useNeighborhoodFilter } from './hooks/useNeighborhoodFilter';
import { busLinesApi } from './api/client';
import { readUrlState, writeUrlState } from './utils/urlState';
import type { LineDetail, MetaInfo } from './api/types';
import './App.css';

export function App() {
  const { selectedLines, activeLineId, select, deselect } = useSelectedLines();
  const {
    neighborhoods,
    filteredNeighborhoods,
    selectedNeighborhoodId,
    neighborhoodLines,
    searchQuery,
    isLoadingNeighborhoods,
    isLoadingLines,
    error: neighborhoodError,
    selectNeighborhood,
    clearFilter,
    setSearchQuery,
  } = useNeighborhoodFilter();

  const [meta, setMeta] = useState<MetaInfo | null>(null);

  // Track whether we've already restored from URL (only do it once on mount)
  const restoredRef = useRef(false);

  // ── Meta (attribution) ────────────────────────────────────────────────────

  useEffect(() => {
    busLinesApi
      .meta()
      .then(setMeta)
      .catch(() => {
        // Non-critical — attribution just won't show
      });
  }, []);

  // ── Restore view from URL on mount ────────────────────────────────────────

  useEffect(() => {
    if (restoredRef.current) return;
    restoredRef.current = true;

    const { neighborhoodId, lineIds } = readUrlState();

    if (neighborhoodId != null) {
      selectNeighborhood(neighborhoodId);
    }

    if (lineIds.length > 0) {
      lineIds.forEach((id) => {
        busLinesApi
          .lineDetail(id)
          .then((detail) => select(detail))
          .catch(() => {
            // Non-critical — individual line may not exist; just skip it
          });
      });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Sync state → URL ──────────────────────────────────────────────────────

  useEffect(() => {
    writeUrlState({
      neighborhoodId: selectedNeighborhoodId,
      lineIds: Array.from(selectedLines.keys()),
    });
  }, [selectedNeighborhoodId, selectedLines]);

  // ── Line selection handlers ───────────────────────────────────────────────

  const handleSelectLine = (detail: LineDetail) => {
    select(detail);
  };

  const handleDeselectLine = (id: number) => {
    deselect(id);
  };

  // ── Neighbourhood boundary ────────────────────────────────────────────────

  const boundary = neighborhoodLines?.boundaryGeoJson ?? null;

  return (
    <div className="app-layout">
      <aside className="app-layout__panel" aria-label="Painel de navegação">
        <NeighborhoodPanel
          neighborhoods={neighborhoods}
          filteredNeighborhoods={filteredNeighborhoods}
          selectedNeighborhoodId={selectedNeighborhoodId}
          neighborhoodLines={neighborhoodLines}
          searchQuery={searchQuery}
          isLoadingLines={isLoadingLines || isLoadingNeighborhoods}
          error={neighborhoodError}
          selectedLines={selectedLines}
          onSelectNeighborhood={selectNeighborhood}
          onClearFilter={clearFilter}
          onSearchQueryChange={setSearchQuery}
          onSelectLine={handleSelectLine}
          onDeselectLine={handleDeselectLine}
        />

        <SearchPanel
          selectedLines={selectedLines}
          onSelectLine={handleSelectLine}
          onDeselectLine={handleDeselectLine}
        />
      </aside>

      <main className="app-layout__map">
        <MapView
          selectedLines={selectedLines}
          activeLineId={activeLineId}
          neighborhoodBoundary={boundary}
        />
        {meta && (
          <Attribution
            attribution={meta.attribution}
            lastImportedAt={meta.lastImportedAt}
          />
        )}
      </main>
    </div>
  );
}

export default App;
