/**
 * Unit/component tests for NeighborhoodPanel.
 *
 * Uses MSW for API stubs when the panel fetches line details on click.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NeighborhoodPanel } from './NeighborhoodPanel';
import type { NeighborhoodPanelProps } from './NeighborhoodPanel';
import type { LineDetail } from '../api/types';
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

  it('section collapses and re-expands on header click', async () => {
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
});
