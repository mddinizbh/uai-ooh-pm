import { useState, useCallback } from 'react';
import { busLinesApi, ApiClientError } from '../api/client';
import type {
  NeighborhoodSummary,
  NeighborhoodLines,
  LineSummary,
  LineDetail,
} from '../api/types';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface NeighborhoodPanelProps {
  neighborhoods: NeighborhoodSummary[];
  filteredNeighborhoods: NeighborhoodSummary[];
  selectedNeighborhoodId: number | null;
  neighborhoodLines: NeighborhoodLines | null;
  searchQuery: string;
  isLoadingLines: boolean;
  error: string | null;
  selectedLines: Map<number, LineDetail>;
  onSelectNeighborhood: (id: number) => void;
  onClearFilter: () => void;
  onSearchQueryChange: (q: string) => void;
  onSelectLine: (detail: LineDetail) => void;
  onDeselectLine: (id: number) => void;
}

// ── Collapsible section ───────────────────────────────────────────────────────

interface RelationSectionProps {
  title: string;
  lines: LineSummary[];
  selectedLines: Map<number, LineDetail>;
  onSelectLine: (detail: LineDetail) => void;
  onDeselectLine: (id: number) => void;
  colorClass: string;
}

function RelationSection({
  title,
  lines,
  selectedLines,
  onSelectLine,
  onDeselectLine,
  colorClass,
}: RelationSectionProps) {
  const [open, setOpen] = useState(true);
  const [loadingLineId, setLoadingLineId] = useState<number | null>(null);
  const [lineError, setLineError] = useState<string | null>(null);

  const handleLineClick = useCallback(
    async (summary: LineSummary) => {
      if (selectedLines.has(summary.id)) {
        onDeselectLine(summary.id);
        return;
      }
      setLoadingLineId(summary.id);
      setLineError(null);
      try {
        const detail = await busLinesApi.lineDetail(summary.id);
        onSelectLine(detail);
      } catch (err) {
        const msg =
          err instanceof ApiClientError
            ? err.message
            : 'Erro ao carregar detalhes da linha.';
        setLineError(msg);
      } finally {
        setLoadingLineId(null);
      }
    },
    [selectedLines, onSelectLine, onDeselectLine],
  );

  return (
    <section
      className={`neighborhood-section neighborhood-section--${colorClass}`}
      aria-label={title}
    >
      <button
        type="button"
        className="neighborhood-section__header"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <span className={`neighborhood-section__dot neighborhood-section__dot--${colorClass}`} />
        <span className="neighborhood-section__title">{title}</span>
        <span className="neighborhood-section__count">({lines.length})</span>
        <span
          className="neighborhood-section__chevron"
          aria-hidden="true"
        >
          {open ? '▲' : '▼'}
        </span>
      </button>

      {open && (
        <ul className="neighborhood-section__lines" aria-label={`Linhas: ${title}`}>
          {lines.length === 0 && (
            <li className="neighborhood-section__empty">Nenhuma linha</li>
          )}
          {lineError && (
            <li role="alert" className="neighborhood-section__error">
              {lineError}
            </li>
          )}
          {lines.map((line) => {
            const isSelected = selectedLines.has(line.id);
            const isLoadingThis = loadingLineId === line.id;
            return (
              <li key={line.id} className="neighborhood-section__line-item">
                <button
                  type="button"
                  className={[
                    'neighborhood-section__line-btn',
                    isSelected ? 'neighborhood-section__line-btn--selected' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onClick={() => void handleLineClick(line)}
                  disabled={isLoadingThis}
                  aria-pressed={isSelected}
                >
                  <span className="neighborhood-section__line-number">
                    {line.shortName}
                  </span>
                  <span className="neighborhood-section__line-name">
                    {line.longName}
                  </span>
                  {isLoadingThis && (
                    <span aria-hidden="true" className="neighborhood-section__spinner">
                      …
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export function NeighborhoodPanel({
  neighborhoods,
  filteredNeighborhoods,
  selectedNeighborhoodId,
  neighborhoodLines,
  searchQuery,
  isLoadingLines,
  error,
  selectedLines,
  onSelectNeighborhood,
  onClearFilter,
  onSearchQueryChange,
  onSelectLine,
  onDeselectLine,
}: NeighborhoodPanelProps) {
  const [listOpen, setListOpen] = useState(false);

  const handleSearchFocus = () => setListOpen(true);
  const handleSearchBlur = () => {
    // Delay so click on list items registers before blur closes the dropdown
    setTimeout(() => setListOpen(false), 150);
  };

  const handleSelectNeighborhood = (id: number) => {
    onSelectNeighborhood(id);
    setListOpen(false);
  };

  const hasSelection = selectedNeighborhoodId !== null;

  return (
    <div className="neighborhood-panel" aria-label="Filtro por bairro">
      {/* ── Search / autocomplete input ── */}
      <div className="neighborhood-panel__search">
        <label htmlFor="neighborhood-search" className="sr-only">
          Buscar bairro
        </label>
        <input
          id="neighborhood-search"
          type="search"
          className="neighborhood-panel__input"
          placeholder="Buscar bairro…"
          value={searchQuery}
          onChange={(e) => {
            onSearchQueryChange(e.target.value);
            setListOpen(true);
          }}
          onFocus={handleSearchFocus}
          onBlur={handleSearchBlur}
          aria-label="Buscar bairro"
          aria-autocomplete="list"
          aria-controls="neighborhood-list"
          aria-expanded={listOpen}
        />

        {/* ── Neighborhood list dropdown ── */}
        {listOpen && (
          <ul
            id="neighborhood-list"
            className="neighborhood-panel__dropdown"
            role="listbox"
            aria-label="Lista de bairros"
          >
            {filteredNeighborhoods.length === 0 && (
              <li className="neighborhood-panel__dropdown-empty">
                Nenhum bairro encontrado
              </li>
            )}
            {filteredNeighborhoods.map((n) => {
              const totalCount =
                n.passesThroughCount + n.departsFromCount + n.arrivesAtCount;
              const isActive = n.id === selectedNeighborhoodId;
              return (
                <li
                  key={n.id}
                  role="option"
                  aria-selected={isActive}
                  className={[
                    'neighborhood-panel__dropdown-item',
                    isActive ? 'neighborhood-panel__dropdown-item--active' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onMouseDown={() => handleSelectNeighborhood(n.id)}
                >
                  <span className="neighborhood-panel__dropdown-name">{n.name}</span>
                  <span className="neighborhood-panel__dropdown-count">
                    {totalCount} linha{totalCount !== 1 ? 's' : ''}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* ── Neighborhood list (always-visible) ── */}
      {!hasSelection && !listOpen && neighborhoods.length > 0 && (
        <ul
          className="neighborhood-panel__list"
          aria-label="Bairros disponíveis"
        >
          {neighborhoods.map((n) => {
            const totalCount =
              n.passesThroughCount + n.departsFromCount + n.arrivesAtCount;
            return (
              <li key={n.id} className="neighborhood-panel__list-item">
                <button
                  type="button"
                  className="neighborhood-panel__list-btn"
                  onClick={() => handleSelectNeighborhood(n.id)}
                  aria-label={`${n.name} — ${totalCount} linhas`}
                >
                  <span className="neighborhood-panel__list-name">{n.name}</span>
                  <span className="neighborhood-panel__list-count">{totalCount}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {/* ── Error ── */}
      {error && (
        <div role="alert" className="neighborhood-panel__error">
          {error}
        </div>
      )}

      {/* ── Loading lines ── */}
      {isLoadingLines && (
        <div aria-live="polite" className="neighborhood-panel__loading">
          Carregando linhas…
        </div>
      )}

      {/* ── Selected neighbourhood: 3 sections ── */}
      {hasSelection && neighborhoodLines && !isLoadingLines && (
        <div className="neighborhood-panel__sections">
          <RelationSection
            title="Passa por aqui"
            lines={neighborhoodLines.passesThrough}
            selectedLines={selectedLines}
            onSelectLine={onSelectLine}
            onDeselectLine={onDeselectLine}
            colorClass="passes"
          />
          <RelationSection
            title="Parte daqui"
            lines={neighborhoodLines.departsFrom}
            selectedLines={selectedLines}
            onSelectLine={onSelectLine}
            onDeselectLine={onDeselectLine}
            colorClass="departs"
          />
          <RelationSection
            title="Chega aqui"
            lines={neighborhoodLines.arrivesAt}
            selectedLines={selectedLines}
            onSelectLine={onSelectLine}
            onDeselectLine={onDeselectLine}
            colorClass="arrives"
          />

          {/* ── Clear filter button ── */}
          <button
            type="button"
            className="neighborhood-panel__clear-btn"
            onClick={onClearFilter}
            aria-label="Limpar filtro de bairro"
          >
            Limpar filtro de bairro
          </button>
        </div>
      )}
    </div>
  );
}
