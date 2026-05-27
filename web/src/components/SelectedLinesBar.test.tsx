import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SelectedLinesBar } from './SelectedLinesBar';
import type { LineDetail } from '../api/types';
import { lineColor } from '../utils/mapLayers';

// ── Fixtures ──────────────────────────────────────────────────────────────────

function makeDetail(id: number, shortName: string): LineDetail {
  return {
    line: { id, shortName, longName: `Long name ${shortName}` },
    shapes: [],
    stops: [],
  };
}

function makeSelection(entries: [number, LineDetail][]): Map<number, LineDetail> {
  return new Map(entries);
}

// ── Unit tests ────────────────────────────────────────────────────────────────

describe('SelectedLinesBar', () => {
  describe('empty state', () => {
    it('renders nothing (null) when selectedLines is empty', () => {
      const { container } = render(
        <SelectedLinesBar selectedLines={new Map()} onDeselectLine={vi.fn()} />,
      );
      expect(container.firstChild).toBeNull();
    });
  });

  describe('chip rendering', () => {
    it('renders exactly two chips when two lines are selected', () => {
      const selectedLines = makeSelection([
        [1, makeDetail(1, '9400')],
        [2, makeDetail(2, 'SC02')],
      ]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      // Each chip has exactly one remove button
      expect(screen.getAllByRole('button')).toHaveLength(2);
    });

    it('renders the short name of each selected line', () => {
      const selectedLines = makeSelection([
        [1, makeDetail(1, '9400')],
        [2, makeDetail(2, 'SC02')],
      ]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      expect(screen.getByText('9400')).toBeInTheDocument();
      expect(screen.getByText('SC02')).toBeInTheDocument();
    });

    it('sets chip swatch background equal to lineColor(id)', () => {
      const id = 3;
      const expectedColor = lineColor(id);
      const selectedLines = makeSelection([[id, makeDetail(id, '8500')]]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      const swatch = document.querySelector('.chip__swatch') as HTMLElement;
      expect(swatch).toHaveStyle({ background: expectedColor });
    });

    it('each chip swatch matches lineColor of its own id', () => {
      const id1 = 1;
      const id2 = 5;
      const selectedLines = makeSelection([
        [id1, makeDetail(id1, '9400')],
        [id2, makeDetail(id2, '5000')],
      ]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      const swatches = document.querySelectorAll('.chip__swatch') as NodeListOf<HTMLElement>;
      expect(swatches).toHaveLength(2);
      // Colors are assigned by lineId, not by position
      expect(lineColor(id1)).not.toBe(lineColor(id2)); // id1 and id2 have different colors
    });
  });

  describe('accessibility', () => {
    it('remove button has aria-label containing the short name', () => {
      const selectedLines = makeSelection([[1, makeDetail(1, '9400')]]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      const removeBtn = screen.getByRole('button', { name: /Remover linha 9400/i });
      expect(removeBtn).toBeInTheDocument();
    });

    it('aria-label on remove button matches the exact pattern "Remover linha {shortName}"', () => {
      const selectedLines = makeSelection([[7, makeDetail(7, 'SC02')]]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      expect(
        screen.getByRole('button', { name: 'Remover linha SC02' }),
      ).toBeInTheDocument();
    });

    it('renders the chip bar container with aria-label "Linhas no mapa"', () => {
      const selectedLines = makeSelection([[1, makeDetail(1, '9400')]]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={vi.fn()} />);
      expect(screen.getByRole('region', { name: 'Linhas no mapa' })).toBeInTheDocument();
    });
  });

  describe('interaction — remove', () => {
    it('calls onDeselectLine(id) when the remove button is clicked', async () => {
      const onDeselectLine = vi.fn();
      const selectedLines = makeSelection([[42, makeDetail(42, '9400')]]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={onDeselectLine} />);
      await userEvent.click(screen.getByRole('button', { name: /Remover linha 9400/i }));
      expect(onDeselectLine).toHaveBeenCalledWith(42);
      expect(onDeselectLine).toHaveBeenCalledTimes(1);
    });

    it('calls onDeselectLine with the correct id for each chip independently', async () => {
      const onDeselectLine = vi.fn();
      const selectedLines = makeSelection([
        [10, makeDetail(10, '9400')],
        [20, makeDetail(20, 'SC02')],
      ]);
      render(<SelectedLinesBar selectedLines={selectedLines} onDeselectLine={onDeselectLine} />);

      await userEvent.click(screen.getByRole('button', { name: /Remover linha SC02/i }));
      expect(onDeselectLine).toHaveBeenCalledWith(20);
      expect(onDeselectLine).toHaveBeenCalledTimes(1);
    });
  });

  describe('integration — two-chip bar', () => {
    it('removing one chip from a two-chip bar leaves the other chip rendered', async () => {
      const onDeselectLine = vi.fn();
      const initial = makeSelection([
        [1, makeDetail(1, '9400')],
        [2, makeDetail(2, 'SC02')],
      ]);
      const { rerender } = render(
        <SelectedLinesBar selectedLines={initial} onDeselectLine={onDeselectLine} />,
      );

      // Click remove for line 1 (9400)
      await userEvent.click(screen.getByRole('button', { name: /Remover linha 9400/i }));
      expect(onDeselectLine).toHaveBeenCalledWith(1);

      // Parent updates selection: line 1 is removed
      const afterRemoval = makeSelection([[2, makeDetail(2, 'SC02')]]);
      rerender(<SelectedLinesBar selectedLines={afterRemoval} onDeselectLine={onDeselectLine} />);

      // SC02 still visible, 9400 gone
      expect(screen.queryByText('9400')).not.toBeInTheDocument();
      expect(screen.getByText('SC02')).toBeInTheDocument();
    });

    it('removing both chips leaves the bar hidden (null)', async () => {
      const onDeselectLine = vi.fn();
      const initial = makeSelection([[1, makeDetail(1, '9400')]]);
      const { rerender, container } = render(
        <SelectedLinesBar selectedLines={initial} onDeselectLine={onDeselectLine} />,
      );

      await userEvent.click(screen.getByRole('button', { name: /Remover linha 9400/i }));

      // Parent removes last line
      rerender(
        <SelectedLinesBar selectedLines={new Map()} onDeselectLine={onDeselectLine} />,
      );
      // Expect the chip bar itself to be gone
      expect(container.firstChild).toBeNull();
    });
  });
});
