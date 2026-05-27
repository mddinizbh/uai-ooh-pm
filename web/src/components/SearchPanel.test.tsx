import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { SearchPanel } from './SearchPanel';
import { LINE_9400, LINE_9401, LINE_9400_DETAIL } from '../test/handlers';
import type { LineDetail } from '../api/types';

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderPanel(
  props: Partial<{
    selectedLines: Map<number, LineDetail>;
    onSelectLine: (d: LineDetail) => void;
    onDeselectLine: (id: number) => void;
  }> = {},
) {
  const onSelectLine = props.onSelectLine ?? vi.fn();
  const onDeselectLine = props.onDeselectLine ?? vi.fn();
  const selectedLines = props.selectedLines ?? new Map<number, LineDetail>();

  render(
    <SearchPanel
      selectedLines={selectedLines}
      onSelectLine={onSelectLine}
      onDeselectLine={onDeselectLine}
    />,
  );

  return { onSelectLine, onDeselectLine };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SearchPanel', () => {
  describe('initial state', () => {
    it('renders the search input', () => {
      renderPanel();
      expect(screen.getByRole('searchbox')).toBeInTheDocument();
    });

    it('does not render an h1 title', () => {
      renderPanel();
      expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
    });

    it('loads all lines on mount (empty query)', async () => {
      renderPanel();
      await waitFor(() => {
        expect(screen.getByText(LINE_9400.shortName)).toBeInTheDocument();
        expect(screen.getByText(LINE_9401.shortName)).toBeInTheDocument();
      });
    });
  });

  describe('search', () => {
    it('issues GET /api/lines?q=9400 when "9400" is typed', async () => {
      let capturedUrl: string | undefined;
      server.use(
        http.get('/api/lines', ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([LINE_9400]);
        }),
      );

      const user = userEvent.setup();
      renderPanel();

      await user.clear(screen.getByRole('searchbox'));
      await user.type(screen.getByRole('searchbox'), '9400');

      await waitFor(
        () => {
          expect(capturedUrl).toBeDefined();
          expect(capturedUrl).toContain('q=9400');
        },
        { timeout: 1000 },
      );
    });

    it('displays filtered results after search', async () => {
      server.use(
        http.get('/api/lines', () => HttpResponse.json([LINE_9400])),
      );

      const user = userEvent.setup();
      renderPanel();

      await user.type(screen.getByRole('searchbox'), '9400');

      await waitFor(() => {
        expect(screen.getByText(LINE_9400.shortName)).toBeInTheDocument();
      });
    });

    it('shows empty state when no lines match the query', async () => {
      server.use(
        http.get('/api/lines', () => HttpResponse.json([])),
      );

      const user = userEvent.setup();
      renderPanel();

      await user.type(screen.getByRole('searchbox'), 'XXXXXXXXX');

      await waitFor(() => {
        expect(screen.getByText(/nenhuma linha encontrada/i)).toBeInTheDocument();
      });
    });

    it('shows animated Lucide loader when fetching line detail', async () => {
      // Block the line detail endpoint so loading state is visible
      server.use(
        http.get('/api/lines/:id', () => new Promise(() => {})),
      );

      const user = userEvent.setup();
      renderPanel();

      // Wait for results to load
      await waitFor(() => screen.getByText(LINE_9400.shortName));

      // Click a line to trigger detail fetch (will hang)
      await user.click(screen.getByText(LINE_9400.shortName).closest('button')!);

      // Spinner should appear: a Lucide SVG with aria-hidden
      await waitFor(() => {
        const spinner = document.querySelector('.search-panel__spinner');
        expect(spinner).toBeTruthy();
        expect(spinner?.getAttribute('aria-hidden')).toBe('true');
        // No raw glyph text — it's an SVG, not a span with '…'
        expect(spinner?.tagName.toLowerCase()).not.toBe('span');
      });
    });
  });

  describe('line selection', () => {
    it('calls onSelectLine with LineDetail when a result is clicked', async () => {
      const onSelectLine = vi.fn();
      const user = userEvent.setup();
      renderPanel({ onSelectLine });

      // Wait for initial results
      await waitFor(() => screen.getByText(LINE_9400.shortName));

      await user.click(screen.getByText(LINE_9400.shortName).closest('button')!);

      await waitFor(() => {
        expect(onSelectLine).toHaveBeenCalledOnce();
        expect(onSelectLine).toHaveBeenCalledWith(
          expect.objectContaining({
            line: expect.objectContaining({ id: LINE_9400.id }),
          }),
        );
      });
    });

    it('calls onDeselectLine when a selected line is clicked', async () => {
      const onDeselectLine = vi.fn();
      const selectedLines = new Map([[LINE_9400.id, LINE_9400_DETAIL]]);
      const user = userEvent.setup();
      renderPanel({ selectedLines, onDeselectLine });

      // Wait for search results to load (the button with the full longName is in
      // the results list — distinct from the shortName span in the selected section)
      const lineBtn = await screen.findByRole('button', {
        name: new RegExp(LINE_9400.longName, 'i'),
      });
      await user.click(lineBtn);

      expect(onDeselectLine).toHaveBeenCalledWith(LINE_9400.id);
    });

    it('marks the button as aria-pressed when the line is selected', async () => {
      const selectedLines = new Map([[LINE_9400.id, LINE_9400_DETAIL]]);
      renderPanel({ selectedLines });

      // Wait for results to load, then scope the query to the results list
      await waitFor(() => {
        const resultsList = screen.getByRole('list', { name: /resultados/i });
        const lineBtn = within(resultsList)
          .getByText(LINE_9400.shortName)
          .closest('button');
        expect(lineBtn).toHaveAttribute('aria-pressed', 'true');
      });
    });

    it('shows a color swatch on selected line rows', async () => {
      const selectedLines = new Map([[LINE_9400.id, LINE_9400_DETAIL]]);
      renderPanel({ selectedLines });

      // Wait for results to render
      await waitFor(() => screen.getByText(LINE_9400.shortName));

      // Swatch should be present on the selected row
      const swatch = document.querySelector('.line-swatch');
      expect(swatch).toBeTruthy();
      expect(swatch?.getAttribute('aria-hidden')).toBe('true');
      // Swatch has an inline background color
      expect((swatch as HTMLElement)?.style.background).toBeTruthy();
    });

    it('does not show a swatch on non-selected line rows', async () => {
      renderPanel(); // no selectedLines

      await waitFor(() => screen.getByText(LINE_9400.shortName));

      // No swatches when nothing is selected
      expect(document.querySelector('.line-swatch')).toBeNull();
    });
  });

  describe('selected lines list removed', () => {
    it('does not render a "Linhas no mapa" section even when lines are selected', () => {
      const selectedLines = new Map([[LINE_9400.id, LINE_9400_DETAIL]]);
      renderPanel({ selectedLines });
      // The selected-lines section has been moved to SelectedLinesBar (task_04/05)
      expect(screen.queryByLabelText(/linhas selecionadas/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/linhas no mapa/i)).not.toBeInTheDocument();
    });
  });

  describe('error handling', () => {
    it('renders an error message when the API returns 500', async () => {
      server.use(
        http.get('/api/lines', () =>
          HttpResponse.json({ status: 500, message: 'Server error' }, { status: 500 }),
        ),
      );

      renderPanel();

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });

    it('does not show a blank screen on API error', async () => {
      server.use(
        http.get('/api/lines', () =>
          HttpResponse.json({ status: 503 }, { status: 503 }),
        ),
      );

      renderPanel();

      await waitFor(() => {
        // Search input should still be visible
        expect(screen.getByRole('searchbox')).toBeInTheDocument();
        // Error is shown, not blank
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });
});
