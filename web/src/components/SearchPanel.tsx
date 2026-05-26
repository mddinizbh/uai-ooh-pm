import { useState, useEffect, useCallback } from 'react';
import { busLinesApi, ApiClientError } from '../api/client';
import type { LineSummary, LineDetail } from '../api/types';

// ── Props ─────────────────────────────────────────────────────────────────────

export interface SearchPanelProps {
  selectedLines: Map<number, LineDetail>;
  onSelectLine: (detail: LineDetail) => void;
  onDeselectLine: (id: number) => void;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function SearchPanel({
  selectedLines,
  onSelectLine,
  onDeselectLine,
}: SearchPanelProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<LineSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingLineId, setLoadingLineId] = useState<number | null>(null);

  // ── Search with debounce ────────────────────────────────────────────────

  useEffect(() => {
    const id = setTimeout(async () => {
      setLoading(true);
      setError(null);
      try {
        const lines = await busLinesApi.searchLines(query);
        setResults(lines);
      } catch (err) {
        const msg =
          err instanceof ApiClientError
            ? err.message
            : 'Erro ao buscar linhas. Tente novamente.';
        setError(msg);
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 300);

    return () => clearTimeout(id);
  }, [query]);

  // ── Select a result ─────────────────────────────────────────────────────

  const handleSelectLine = useCallback(
    async (summary: LineSummary) => {
      if (selectedLines.has(summary.id)) {
        onDeselectLine(summary.id);
        return;
      }

      setLoadingLineId(summary.id);
      setError(null);
      try {
        const detail = await busLinesApi.lineDetail(summary.id);
        onSelectLine(detail);
      } catch (err) {
        const msg =
          err instanceof ApiClientError
            ? err.message
            : 'Erro ao carregar detalhes da linha.';
        setError(msg);
      } finally {
        setLoadingLineId(null);
      }
    },
    [selectedLines, onSelectLine, onDeselectLine],
  );

  // ── Render ──────────────────────────────────────────────────────────────

  return (
    <aside className="search-panel" aria-label="Painel de busca de linhas">
      <div className="search-panel__header">
        <h1 className="search-panel__title">Linhas de BH</h1>
      </div>

      <div className="search-panel__search">
        <label htmlFor="line-search" className="sr-only">
          Buscar linha por número ou nome
        </label>
        <input
          id="line-search"
          type="search"
          placeholder="Buscar linha (ex.: 9400)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="search-panel__input"
          aria-label="Buscar linha por número ou nome"
        />
      </div>

      {error && (
        <div role="alert" className="search-panel__error">
          {error}
        </div>
      )}

      {loading && (
        <div aria-live="polite" className="search-panel__loading">
          Carregando…
        </div>
      )}

      {!loading && !error && (
        <ul className="search-panel__results" aria-label="Resultados da busca">
          {results.map((line) => {
            const isSelected = selectedLines.has(line.id);
            const isLoadingThis = loadingLineId === line.id;
            return (
              <li key={line.id} className="search-panel__result-item">
                <button
                  type="button"
                  onClick={() => handleSelectLine(line)}
                  disabled={isLoadingThis}
                  aria-pressed={isSelected}
                  className={[
                    'search-panel__line-btn',
                    isSelected ? 'search-panel__line-btn--selected' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  <span className="search-panel__line-number">
                    {line.shortName}
                  </span>
                  <span className="search-panel__line-name">
                    {line.longName}
                  </span>
                  {isLoadingThis && (
                    <span aria-hidden="true" className="search-panel__spinner">
                      …
                    </span>
                  )}
                </button>
              </li>
            );
          })}
          {results.length === 0 && !loading && query.length > 0 && (
            <li className="search-panel__empty">
              Nenhuma linha encontrada para "{query}"
            </li>
          )}
        </ul>
      )}

      {selectedLines.size > 0 && (
        <section
          className="search-panel__selected"
          aria-label="Linhas selecionadas"
        >
          <h2 className="search-panel__selected-title">
            Linhas no mapa ({selectedLines.size})
          </h2>
          <ul>
            {Array.from(selectedLines.values()).map((detail) => (
              <li key={detail.line.id} className="search-panel__selected-item">
                <span>{detail.line.shortName}</span>
                <button
                  type="button"
                  onClick={() => onDeselectLine(detail.line.id)}
                  aria-label={`Remover linha ${detail.line.shortName}`}
                  className="search-panel__remove-btn"
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </aside>
  );
}
