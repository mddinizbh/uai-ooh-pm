import { http, HttpResponse } from 'msw';
import type { LineSummary, LineDetail, MetaInfo, NeighborhoodSummary, NeighborhoodLines } from '../api/types';

// ── Fixture data ──────────────────────────────────────────────────────────────

export const LINE_9400: LineSummary = {
  id: 1,
  shortName: '9400',
  longName: 'Bairro Belvedere - Centro',
};

export const LINE_9401: LineSummary = {
  id: 2,
  shortName: '9401',
  longName: 'Bairro Belvedere - UFMG',
};

export const LINE_9400_DETAIL: LineDetail = {
  line: LINE_9400,
  shapes: [
    {
      id: 10,
      direction: 0,
      geometry: {
        type: 'LineString',
        coordinates: [
          [-43.9386, -19.9191],
          [-43.9300, -19.9100],
          [-43.9200, -19.9000],
        ],
      },
    },
    {
      id: 11,
      direction: 1,
      geometry: {
        type: 'LineString',
        coordinates: [
          [-43.9200, -19.9000],
          [-43.9300, -19.9100],
          [-43.9386, -19.9191],
        ],
      },
    },
  ],
  stops: [],
};

export const LINE_9401_DETAIL: LineDetail = {
  line: LINE_9401,
  shapes: [
    {
      id: 20,
      direction: 0,
      geometry: {
        type: 'LineString',
        coordinates: [
          [-43.9386, -19.9191],
          [-43.9700, -19.8700],
        ],
      },
    },
  ],
  stops: [],
};

export const META: MetaInfo = {
  lastImportedAt: '2026-05-25T10:30:00Z',
  attribution:
    'Dados abertos PBH/BHTRANS — GTFS e limites de bairros — Licença CC-BY 4.0.',
};

// ── Neighbourhood fixtures ────────────────────────────────────────────────────

const POLYGON_BELVEDERE = {
  type: 'Polygon' as const,
  coordinates: [
    [
      [-43.975, -19.975],
      [-43.960, -19.975],
      [-43.960, -19.960],
      [-43.975, -19.960],
      [-43.975, -19.975],
    ],
  ],
};

const POLYGON_SAVASSI = {
  type: 'Polygon' as const,
  coordinates: [
    [
      [-43.945, -19.940],
      [-43.930, -19.940],
      [-43.930, -19.925],
      [-43.945, -19.925],
      [-43.945, -19.940],
    ],
  ],
};

export const NEIGHBORHOOD_BELVEDERE: NeighborhoodSummary = {
  id: 10,
  name: 'Belvedere',
  passesThroughCount: 1,
  departsFromCount: 1,
  arrivesAtCount: 0,
};

export const NEIGHBORHOOD_SAVASSI: NeighborhoodSummary = {
  id: 11,
  name: 'Savassi',
  passesThroughCount: 2,
  departsFromCount: 0,
  arrivesAtCount: 1,
};

export const NEIGHBORHOOD_BELVEDERE_LINES: NeighborhoodLines = {
  neighborhoodId: 10,
  neighborhoodName: 'Belvedere',
  boundaryGeoJson: POLYGON_BELVEDERE,
  passesThrough: [LINE_9400],
  departsFrom: [LINE_9401],
  arrivesAt: [],
};

export const NEIGHBORHOOD_SAVASSI_LINES: NeighborhoodLines = {
  neighborhoodId: 11,
  neighborhoodName: 'Savassi',
  boundaryGeoJson: POLYGON_SAVASSI,
  passesThrough: [LINE_9400, LINE_9401],
  departsFrom: [],
  arrivesAt: [LINE_9400],
};

// ── Default handlers ──────────────────────────────────────────────────────────

export const handlers = [
  http.get('/api/lines', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q') ?? '';
    const all = [LINE_9400, LINE_9401];
    const filtered = q
      ? all.filter(
          (l) =>
            l.shortName.toLowerCase().includes(q.toLowerCase()) ||
            l.longName.toLowerCase().includes(q.toLowerCase()),
        )
      : all;
    return HttpResponse.json(filtered);
  }),

  http.get('/api/lines/:id', ({ params }) => {
    const id = Number(params.id);
    if (id === LINE_9400.id) return HttpResponse.json(LINE_9400_DETAIL);
    if (id === LINE_9401.id) return HttpResponse.json(LINE_9401_DETAIL);
    return HttpResponse.json(
      { status: 404, message: 'Linha não encontrada', timestamp: new Date().toISOString() },
      { status: 404 },
    );
  }),

  http.get('/api/meta', () => HttpResponse.json(META)),

  http.get('/api/neighborhoods', () =>
    HttpResponse.json([NEIGHBORHOOD_BELVEDERE, NEIGHBORHOOD_SAVASSI]),
  ),

  http.get('/api/neighborhoods/:id/lines', ({ params }) => {
    const id = Number(params.id);
    if (id === NEIGHBORHOOD_BELVEDERE.id)
      return HttpResponse.json(NEIGHBORHOOD_BELVEDERE_LINES);
    if (id === NEIGHBORHOOD_SAVASSI.id)
      return HttpResponse.json(NEIGHBORHOOD_SAVASSI_LINES);
    return HttpResponse.json(
      { status: 404, message: 'Bairro não encontrado', timestamp: new Date().toISOString() },
      { status: 404 },
    );
  }),
];
