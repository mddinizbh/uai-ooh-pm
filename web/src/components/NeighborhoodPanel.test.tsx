/**
 * Unit/component tests for NeighborhoodPanel.
 *
 * Uses MSW for API stubs when the panel fetches line details on click.
 */
import { describe, it, expect, vi } from 'vitest';
import { useState } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NeighborhoodPanel } from './NeighborhoodPanel';
import type { NeighborhoodPanelProps } from './NeighborhoodPanel';
import type { LineDetail } from '../api/types';
import { lineColor } from '../utils/mapLayers';

// ── Color helper ──────────────────────────────────────────────────────────────
// jsdom normalizes hex colors to rgb() in style.background — convert before comparing.

function hexToRgb(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgb(${r}, ${g}, ${b})`;
}
import {
  NEIGHBORHOOD_BELVEDERE,
  NEIGHBORHOOD_SAVASSI,
  NEIGHBORHOOD_BELVEDERE_LINES,
  LINE_9400_DETAIL,
  LINE_9401_DETAIL,
} from '../test/handlers';

// ── Default props factory ─────────────────────────────────────────────────────

const noop = () => {};

function makeProps(overrides: Partial<NeighborhoodPanelProps> = {}): NeighborhoodPanelProps {
  return {
    neighborhoods: [NEIGHBORHOOD_BELVEDERE, NEIGHBORHOOD_SAVASSI],
    filteredNeighborhoods: [NEIGHBORHOOD_BELVEDERE, NEIGHBORHOOD_SAVASSI],
    selectedNeighborhoodId: null,
    neighborhoodLines: null,
    searchQuery: '',
    isLoadingLines: false,
    error: null,
    selectedLines: new Map<number, LineDetail>(),
    onSelectNeighborhood: noop,
    onClearFilter: noop,
    onSearchQueryChange: noop,
    onSelectLine: noop,
    onDeselectLine: noop,
    ...overrides,
  };
}

// ── Neighbourhood list ────────────────────────────────────────────────────────

describe('NeighborhoodPanel — list', () => {
  it('shows the neighbourhood list when no neighbourhood is selected', () => {
    render(<NeighborhoodPanel {...makeProps()} />);
    expect(screen.getByText('Belvedere')).toBeInTheDocument();
    expect(screen.getByText('Savassi')).toBeInTheDocument();
  });

  it('calls onSelectNeighborhood when a list item is clicked', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(<NeighborhoodPanel {...makeProps({ onSelectNeighborhood: onSelect })} />);

    await user.click(screen.getByRole('button', { name: /Belvedere/i }));
    expect(onSelect).toHaveBeenCalledWith(NEIGHBORHOOD_BELVEDERE.id);
  });
});

// ── Search / autocomplete ─────────────────────────────────────────────────────

describe('NeighborhoodPanel — search', () => {
  it('shows the search input', () => {
    render(<NeighborhoodPanel {...makeProps()} />);
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
  });

  it('calls onSearchQueryChange when user types', async () => {
    const user = userEvent.setup();
    const onQueryChange = vi.fn();
    render(<NeighborhoodPanel {...makeProps({ onSearchQueryChange: onQueryChange })} />);

    await user.type(screen.getByRole('searchbox'), 'Sav');
    expect(onQueryChange).toHaveBeenCalledWith(expect.stringContaining('S'));
  });

  it('shows filtered dropdown when typing', async () => {
    const user = userEvent.setup();
    render(
      <NeighborhoodPanel
        {...makeProps({
          filteredNeighborhoods: [NEIGHBORHOOD_SAVASSI],
          searchQuery: 'Sav',
        })}
      />,
    );

    await user.click(screen.getByRole('searchbox'));
    expect(screen.getByRole('option', { name: /Savassi/i })).toBeInTheDocument();
  });

  it('calls onSelectNeighborhood when dropdown item is clicked', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    render(
      <NeighborhoodPanel
        {...makeProps({
          onSelectNeighborhood: onSelect,
          filteredNeighborhoods: [NEIGHBORHOOD_SAVASSI],
          searchQuery: 'Sav',
        })}
      />,
    );

    await user.click(screen.getByRole('searchbox'));
    await user.click(screen.getByRole('option', { name: /Savassi/i }));
    expect(onSelect).toHaveBeenCalledWith(NEIGHBORHOOD_SAVASSI.id);
  });
});

// ── Three sections ────────────────────────────────────────────────────────────

describe('NeighborhoodPanel — three relation sections', () => {
  const propsWithSelection = makeProps({
    selectedNeighborhoodId: NEIGHBORHOOD_BELVEDERE.id,
    neighborhoodLines: NEIGHBORHOOD_BELVEDERE_LINES,
    searchQuery: 'Belvedere',
  });

  it('renders all three section titles', () => {
    render(<NeighborhoodPanel {...propsWithSelection} />);
    expect(screen.getByText('Passa por aqui')).toBeInTheDocument();
    expect(screen.getByText('Parte daqui')).toBeInTheDocument();
    expect(screen.getByText('Chega aqui')).toBeInTheDocument();
  });

  it('renders correct per-section counts in the header', () => {
    render(<NeighborhoodPanel {...propsWithSelection} />);
    // passesThrough=1, departsFrom=1, arrivesAt=0
    const counts = document.querySelectorAll('.neighborhood-section__count');
    const countTexts = Array.from(counts).map((el) => el.textContent);
    // Belvedere: passes=1, departs=1, arrives=0
    expect(countTexts).toContain('(1)');
    expect(countTexts).toContain('(0)');
    expect(countTexts.filter((t) => t === '(1)')).toHaveLength(2);
    expect(countTexts.filter((t) => t === '(0)')).toHaveLength(1);
  });

  it('shows line numbers/names inside sections', () => {
    render(<NeighborhoodPanel {...propsWithSelection} />);
    // LINE_9400 in passesThrough, LINE_9401 in departsFrom
    expect(screen.getAllByText('9400')).not.toHaveLength(0);
    expect(screen.getAllByText('9401')).not.toHaveLength(0);
  });

  it('section collapses and re-expands on header click (aria-expanded toggles)', async () => {
    const user = userEvent.setup();
    render(<NeighborhoodPanel {...propsWithSelection} />);

    // Collapse the "Passa por aqui" section
    const header = screen.getByRole('button', { name: /Passa por aqui/i });
    await user.click(header);
    expect(header).toHaveAttribute('aria-expanded', 'false');

    // Expand again
    await user.click(header);
    expect(header).toHaveAttribute('aria-expanded', 'true');
  });

  it('section header uses a Lucide ChevronDown icon (SVG), not ▲/▼ glyphs', () => {
    render(<NeighborhoodPanel {...propsWithSelection} />);

    // No raw glyph characters
    expect(screen.queryByText('▲')).not.toBeInTheDocument();
    expect(screen.queryByText('▼')).not.toBeInTheDocument();

    // Each section header contains an SVG (Lucide ChevronDown)
    const header = screen.getByRole('button', { name: /Passa por aqui/i });
    const svg = header.querySelector('svg');
    expect(svg).toBeTruthy();
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
  });

  it('clicking a line in a section calls onSelectLine with its LineDetail', async () => {
    const user = userEvent.setup();
    const onSelectLine = vi.fn();

    render(
      <NeighborhoodPanel
        {...propsWithSelection}
        onSelectLine={onSelectLine}
      />,
    );

    // Click the 9400 line button (in passesThrough section)
    const lineBtn = screen.getAllByRole('button', { name: /9400/i })[0];
    await user.click(lineBtn);

    await waitFor(() => {
      expect(onSelectLine).toHaveBeenCalledWith(LINE_9400_DETAIL);
    });
  });

  it('clicking an already-selected line calls onDeselectLine', async () => {
    const user = userEvent.setup();
    const onDeselectLine = vi.fn();
    const selectedLines = new Map([[LINE_9400_DETAIL.line.id, LINE_9400_DETAIL]]);

    render(
      <NeighborhoodPanel
        {...propsWithSelection}
        selectedLines={selectedLines}
        onDeselectLine={onDeselectLine}
      />,
    );

    const lineBtn = screen.getAllByRole('button', { name: /9400/i })[0];
    await user.click(lineBtn);

    expect(onDeselectLine).toHaveBeenCalledWith(LINE_9400_DETAIL.line.id);
  });

  it('selected lines are aria-pressed=true in the sections', () => {
    const selectedLines = new Map([[LINE_9401_DETAIL.line.id, LINE_9401_DETAIL]]);

    render(
      <NeighborhoodPanel
        {...propsWithSelection}
        selectedLines={selectedLines}
      />,
    );

    // 9401 is in departsFrom section — should have aria-pressed=true
    const btn9401 = screen
      .getAllByRole('button', { name: /9401/i })
      .find((b) => b.getAttribute('aria-pressed') !== null);
    expect(btn9401).toBeDefined();
    expect(btn9401?.getAttribute('aria-pressed')).toBe('true');
  });

  it('shows a color swatch on selected line rows', () => {
    const selectedLines = new Map([[LINE_9400_DETAIL.line.id, LINE_9400_DETAIL]]);

    render(
      <NeighborhoodPanel
        {...propsWithSelection}
        selectedLines={selectedLines}
      />,
    );

    // LINE_9400 (id=1) is in passesThrough — should have a swatch
    const swatch = document.querySelector('.line-swatch');
    expect(swatch).toBeTruthy();
    expect(swatch?.getAttribute('aria-hidden')).toBe('true');
    // Swatch color matches lineColor(LINE_9400.id) — jsdom normalizes hex→rgb
    expect((swatch as HTMLElement)?.style.background).toBe(
      hexToRgb(lineColor(LINE_9400_DETAIL.line.id)),
    );
  });

  it('does not show a swatch on non-selected line rows', () => {
    render(<NeighborhoodPanel {...propsWithSelection} />);
    // No selectedLines — no swatches
    expect(document.querySelector('.line-swatch')).toBeNull();
  });
});

// ── Empty / first-run hint ────────────────────────────────────────────────────

describe('NeighborhoodPanel — empty hint', () => {
  it('shows the empty hint when there are no neighborhoods and nothing selected', () => {
    render(
      <NeighborhoodPanel
        {...makeProps({
          neighborhoods: [],
          filteredNeighborhoods: [],
          selectedLines: new Map(),
          selectedNeighborhoodId: null,
        })}
      />,
    );
    expect(screen.getByText(/busque um bairro/i)).toBeInTheDocument();
  });

  it('does not show the empty hint when neighborhoods are available', () => {
    render(<NeighborhoodPanel {...makeProps()} />);
    expect(screen.queryByText(/busque um bairro/i)).not.toBeInTheDocument();
  });

  it('does not show the empty hint when a neighborhood is selected', () => {
    render(
      <NeighborhoodPanel
        {...makeProps({
          neighborhoods: [],
          filteredNeighborhoods: [],
          selectedNeighborhoodId: NEIGHBORHOOD_BELVEDERE.id,
          neighborhoodLines: NEIGHBORHOOD_BELVEDERE_LINES,
        })}
      />,
    );
    expect(screen.queryByText(/busque um bairro/i)).not.toBeInTheDocument();
  });

  it('does not show the empty hint when lines are selected', () => {
    render(
      <NeighborhoodPanel
        {...makeProps({
          neighborhoods: [],
          filteredNeighborhoods: [],
          selectedLines: new Map([[LINE_9400_DETAIL.line.id, LINE_9400_DETAIL]]),
        })}
      />,
    );
    expect(screen.queryByText(/busque um bairro/i)).not.toBeInTheDocument();
  });
});

// ── Integration: select/deselect cycle with swatch ───────────────────────────

describe('NeighborhoodPanel — integration: select/deselect with swatch', () => {
  it('selecting a line shows its swatch; deselecting removes it', async () => {
    const user = userEvent.setup();

    // Stateful wrapper to simulate App-level state management
    function PanelWithState() {
      const [selectedLines, setSelectedLines] = useState(
        new Map<number, LineDetail>(),
      );

      const onSelectLine = (detail: LineDetail) => {
        setSelectedLines((prev) => new Map(prev).set(detail.line.id, detail));
      };

      const onDeselectLine = (id: number) => {
        setSelectedLines((prev) => {
          const next = new Map(prev);
          next.delete(id);
          return next;
        });
      };

      return (
        <NeighborhoodPanel
          {...makeProps({
            selectedNeighborhoodId: NEIGHBORHOOD_BELVEDERE.id,
            neighborhoodLines: NEIGHBORHOOD_BELVEDERE_LINES,
            selectedLines,
            onSelectLine,
            onDeselectLine,
          })}
        />
      );
    }

    render(<PanelWithState />);

    // Before selection: no swatch
    expect(document.querySelector('.line-swatch')).toBeNull();

    // Click to select LINE_9400
    const lineBtn = screen.getAllByRole('button', { name: /9400/i })[0];
    await user.click(lineBtn);

    // After selection: swatch appears with correct color
    await waitFor(() => {
      const swatch = document.querySelector('.line-swatch');
      expect(swatch).toBeTruthy();
      // jsdom normalizes hex→rgb when reading back from style
      expect((swatch as HTMLElement)?.style.background).toBe(
        hexToRgb(lineColor(LINE_9400_DETAIL.line.id)),
      );
      // Button is aria-pressed
      expect(lineBtn).toHaveAttribute('aria-pressed', 'true');
    });

    // Click again to deselect
    await user.click(lineBtn);

    // After deselection: swatch gone, aria-pressed=false
    await waitFor(() => {
      expect(document.querySelector('.line-swatch')).toBeNull();
      expect(lineBtn).toHaveAttribute('aria-pressed', 'false');
    });
  });
});

// ── Clear filter ──────────────────────────────────────────────────────────────

describe('NeighborhoodPanel — clear filter', () => {
  it('shows the clear filter button when a neighbourhood is selected', () => {
    render(
      <NeighborhoodPanel
        {...makeProps({
          selectedNeighborhoodId: NEIGHBORHOOD_BELVEDERE.id,
          neighborhoodLines: NEIGHBORHOOD_BELVEDERE_LINES,
          searchQuery: 'Belvedere',
        })}
      />,
    );
    expect(
      screen.getByRole('button', { name: /limpar filtro de bairro/i }),
    ).toBeInTheDocument();
  });

  it('does not show the clear filter button when no neighbourhood is selected', () => {
    render(<NeighborhoodPanel {...makeProps()} />);
    expect(
      screen.queryByRole('button', { name: /limpar filtro de bairro/i }),
    ).not.toBeInTheDocument();
  });

  it('calls onClearFilter when clear button is clicked', async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();

    render(
      <NeighborhoodPanel
        {...makeProps({
          selectedNeighborhoodId: NEIGHBORHOOD_BELVEDERE.id,
          neighborhoodLines: NEIGHBORHOOD_BELVEDERE_LINES,
          searchQuery: 'Belvedere',
          onClearFilter: onClear,
        })}
      />,
    );

    await user.click(screen.getByRole('button', { name: /limpar filtro de bairro/i }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});

// ── Error / loading ───────────────────────────────────────────────────────────

describe('NeighborhoodPanel — states', () => {
  it('shows error message when error prop is set', () => {
    render(<NeighborhoodPanel {...makeProps({ error: 'Erro de teste' })} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Erro de teste');
  });

  it('shows loading indicator when isLoadingLines is true', () => {
    render(<NeighborhoodPanel {...makeProps({ isLoadingLines: true })} />);
    expect(screen.getByText(/Carregando linhas/i)).toBeInTheDocument();
  });

  it('loading indicator contains a Lucide spinner (aria-hidden SVG)', () => {
    render(<NeighborhoodPanel {...makeProps({ isLoadingLines: true })} />);
    const loadingEl = document.querySelector('.neighborhood-panel__loading');
    expect(loadingEl).toBeTruthy();
    const svg = loadingEl?.querySelector('svg[aria-hidden="true"]');
    expect(svg).toBeTruthy();
  });
});
