/**
 * Integration tests for the full App:
 *  - search → select → route rendered on map (existing)
 *  - neighbourhood filter → group sections → select line from section
 *  - URL state encode/restore
 *
 * Both the API (MSW) and maplibre-gl are mocked.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from './test/server';
import { App } from './App';
import {
  LINE_9400,
  LINE_9400_DETAIL,
  NEIGHBORHOOD_BELVEDERE,
} from './test/handlers';

vi.mock('maplibre-gl');
vi.mock('maplibre-gl/dist/maplibre-gl.css', () => ({}));

// Helper to set window.location.search for URL-restore tests
function setLocationSearch(search: string) {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { ...window.location, search, pathname: '/' },
  });
}

import { Map as MockMap } from 'maplibre-gl';

// Type helper to access mock-only `lastInstance` static property
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

// Reset location.search before each test so URL restore doesn't bleed across
beforeEach(() => {
  setLocationSearch('');
});

// Helper: find the line button in search results by its longName
// (avoids confusion with the "shortName only" span in the selected section)
const findLineButton = (longName: string) =>
  screen.findByRole('button', { name: new RegExp(longName, 'i') });

// The line-search input (distinct from the neighbourhood-search input)
const getLineSearchInput = () =>
  screen.getByRole('searchbox', { name: /buscar linha/i });

describe('App — integration: search → select → route on map', () => {
  it('renders the search panel and map region', async () => {
    render(<App />);
    expect(getLineSearchInput()).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /mapa/i })).toBeInTheDocument();
  });

  it('search "9400" lists the matching line', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.clear(getLineSearchInput());
    await user.type(getLineSearchInput(), '9400');

    // The button for LINE_9400 should appear in search results
    await findLineButton(LINE_9400.longName);
  });

  it('selecting a search result causes its route to appear on the map', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Wait for initial results, then click the LINE_9400 button
    const lineBtn = await findLineButton(LINE_9400.longName);
    await user.click(lineBtn);

    // After selection the map should have source and layer added
    await waitFor(() => {
      const map = getMockMap();
      expect(map.addSource).toHaveBeenCalledWith(
        `line-source-${LINE_9400.id}`,
        expect.objectContaining({ type: 'geojson' }),
      );
      expect(map.addLayer).toHaveBeenCalledWith(
        expect.objectContaining({ id: `line-layer-${LINE_9400.id}` }),
      );
    });
  });

  it('selected line appears in the "Linhas no mapa" section', async () => {
    const user = userEvent.setup();
    render(<App />);

    const lineBtn = await findLineButton(LINE_9400.longName);
    await user.click(lineBtn);

    await waitFor(() => {
      expect(screen.getByLabelText(/linhas selecionadas/i)).toBeInTheDocument();
    });
  });

  it('deselecting a line removes it from the map', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Select the line
    const lineBtn = await findLineButton(LINE_9400.longName);
    await user.click(lineBtn);

    await waitFor(() => screen.getByLabelText(/linhas selecionadas/i));

    const map = getMockMap();
    // Simulate layers being "present" on the map
    map.getLayer.mockReturnValue({ id: `line-layer-${LINE_9400.id}` });
    map.getSource.mockReturnValue({ type: 'geojson' });

    // Deselect via the remove button in the selected list
    const removeBtn = screen.getByRole('button', {
      name: new RegExp(`remover linha ${LINE_9400.shortName}`, 'i'),
    });
    await user.click(removeBtn);

    await waitFor(() => {
      expect(map.removeLayer).toHaveBeenCalledWith(`line-layer-${LINE_9400.id}`);
      expect(map.removeSource).toHaveBeenCalledWith(`line-source-${LINE_9400.id}`);
    });
  });

  it('shows attribution when meta API responds', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/CC-BY/i)).toBeInTheDocument();
    });
  });

  it('API error renders error state, not blank screen', async () => {
    server.use(
      http.get('/api/lines', () =>
        HttpResponse.json(
          { status: 500, message: 'Server error' },
          { status: 500 },
        ),
      ),
    );

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      // Line search input still visible — not a blank screen
      expect(getLineSearchInput()).toBeInTheDocument();
    });
  });

  it('fetches line detail from /api/lines/{id} when a line is selected', async () => {
    let lineDetailFetched = false;
    server.use(
      http.get(`/api/lines/${LINE_9400.id}`, () => {
        lineDetailFetched = true;
        return HttpResponse.json(LINE_9400_DETAIL);
      }),
    );

    const user = userEvent.setup();
    render(<App />);

    const lineBtn = await findLineButton(LINE_9400.longName);
    await user.click(lineBtn);

    await waitFor(() => {
      expect(lineDetailFetched).toBe(true);
    });
  });
});

// ── Neighbourhood filter integration ─────────────────────────────────────────

describe('App — integration: neighbourhood filter', () => {
  it('renders neighbourhood search input', async () => {
    render(<App />);
    // The neighbourhood panel search input has label "Buscar bairro"
    expect(screen.getByLabelText(/buscar bairro/i)).toBeInTheDocument();
  });

  it('selecting a neighbourhood shows the three grouped sections', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Wait for neighbourhood list to load, then click Belvedere
    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);

    await waitFor(() => {
      expect(screen.getByText('Passa por aqui')).toBeInTheDocument();
      expect(screen.getByText('Parte daqui')).toBeInTheDocument();
      expect(screen.getByText('Chega aqui')).toBeInTheDocument();
    });
  });

  it('selecting a line from a section adds it to the map', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Select the Belvedere neighbourhood
    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);

    // Wait for the sections to appear
    await screen.findByText('Passa por aqui');

    // Click line 9400 in the "Passa por aqui" section
    const lineBtn = screen.getAllByRole('button', { name: /9400/i })[0];
    await user.click(lineBtn);

    await waitFor(() => {
      const map = getMockMap();
      expect(map.addSource).toHaveBeenCalledWith(
        `line-source-${LINE_9400.id}`,
        expect.objectContaining({ type: 'geojson' }),
      );
    });
  });

  it('neighbourhood boundary layer is added when a neighbourhood is selected', async () => {
    const user = userEvent.setup();
    render(<App />);

    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);

    await waitFor(() => {
      const map = getMockMap();
      expect(map.addSource).toHaveBeenCalledWith(
        'neighborhood-boundary-source',
        expect.objectContaining({ type: 'geojson' }),
      );
    });
  });

  it('clear neighbourhood filter removes boundary layer but keeps selected lines', async () => {
    const user = userEvent.setup();
    render(<App />);

    // Select neighbourhood + select a line
    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);
    await screen.findByText('Passa por aqui');

    const lineBtn = screen.getAllByRole('button', { name: /9400/i })[0];
    await user.click(lineBtn);
    await waitFor(() => screen.getByLabelText(/linhas selecionadas/i));

    // Simulate layers present on map
    const map = getMockMap();
    map.getLayer.mockImplementation((id: string) => (id ? { id } : null));
    map.getSource.mockImplementation((id: string) => (id ? { type: 'geojson' } : null));

    // Clear the neighbourhood filter
    const clearBtn = screen.getByRole('button', { name: /limpar filtro de bairro/i });
    await user.click(clearBtn);

    await waitFor(() => {
      // Boundary should be removed
      expect(map.removeLayer).toHaveBeenCalledWith('neighborhood-boundary-fill');
      expect(map.removeLayer).toHaveBeenCalledWith('neighborhood-boundary-outline');
      // But selected lines section is still visible
      expect(screen.getByLabelText(/linhas selecionadas/i)).toBeInTheDocument();
    });
  });
});

// ── URL state integration ─────────────────────────────────────────────────────

describe('App — integration: URL state', () => {
  it('restores neighbourhood from URL ?n=<id>', async () => {
    // Pre-set URL with neighbourhood ID
    setLocationSearch(`?n=${NEIGHBORHOOD_BELVEDERE.id}`);

    render(<App />);

    // The neighbourhood sections should appear after restoring from URL
    await waitFor(() => {
      expect(screen.getByText('Passa por aqui')).toBeInTheDocument();
    });
  });

  it('restores selected lines from URL ?lines=<id>', async () => {
    setLocationSearch(`?lines=${LINE_9400.id}`);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByLabelText(/linhas selecionadas/i)).toBeInTheDocument();
    });
  });

  it('restores both neighbourhood and lines from a full shared URL', async () => {
    setLocationSearch(`?n=${NEIGHBORHOOD_BELVEDERE.id}&lines=${LINE_9400.id}`);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Passa por aqui')).toBeInTheDocument();
      expect(screen.getByLabelText(/linhas selecionadas/i)).toBeInTheDocument();
    });
  });

  it('selecting a neighbourhood updates the URL ?n= param', async () => {
    const replaceStateSpy = vi.spyOn(window.history, 'replaceState');
    const user = userEvent.setup();
    render(<App />);

    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);

    await waitFor(() => {
      const calls = replaceStateSpy.mock.calls;
      const lastUrl = calls[calls.length - 1]?.[2] as string;
      expect(lastUrl).toContain(`n=${NEIGHBORHOOD_BELVEDERE.id}`);
    });
  });

  it('selecting a line updates the URL ?lines= param', async () => {
    const replaceStateSpy = vi.spyOn(window.history, 'replaceState');
    const user = userEvent.setup();
    render(<App />);

    const lineBtn = await findLineButton(LINE_9400.longName);
    await user.click(lineBtn);

    await waitFor(() => {
      const calls = replaceStateSpy.mock.calls;
      const lastUrl = calls[calls.length - 1]?.[2] as string;
      expect(lastUrl).toContain(`lines=${LINE_9400.id}`);
    });
  });
});

// ── MapView — boundary layer tests ────────────────────────────────────────────

describe('App — boundary layer', () => {
  it('boundary layer is removed when clear filter is called', async () => {
    const user = userEvent.setup();
    render(<App />);

    const belvBtn = await screen.findByRole('button', { name: /Belvedere/i });
    await user.click(belvBtn);

    // Wait for sections to appear (neighbourhood loaded)
    await screen.findByText('Passa por aqui');

    const map = getMockMap();
    map.getLayer.mockImplementation((id: string) => (id ? { id } : null));
    map.getSource.mockImplementation((id: string) => (id ? { type: 'geojson' } : null));

    const clearBtn = screen.getByRole('button', { name: /limpar filtro de bairro/i });
    await user.click(clearBtn);

    await waitFor(() => {
      expect(map.removeSource).toHaveBeenCalledWith('neighborhood-boundary-source');
    });
  });
});
