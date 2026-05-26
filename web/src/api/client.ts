import type {
  LineSummary,
  LineDetail,
  NeighborhoodSummary,
  NeighborhoodLines,
  MetaInfo,
  ApiErrorBody,
} from './types';

// Same-origin API — no CORS needed (ADR-005)
const BASE_URL = '/api';

// ── Error ─────────────────────────────────────────────────────────────────────

export class ApiClientError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
  }
}

// ── Internal fetch helper ─────────────────────────────────────────────────────

async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`);
  if (!response.ok) {
    const body: Partial<ApiErrorBody> = await response
      .json()
      .catch(() => ({}));
    throw new ApiClientError(
      response.status,
      body.message ?? `Request failed with status ${response.status}`,
    );
  }
  return response.json() as Promise<T>;
}

// ── Public API client ─────────────────────────────────────────────────────────

export const busLinesApi = {
  /**
   * Search bus lines by number or name.
   * Calls GET /api/lines?q=<query>
   */
  searchLines(q: string = ''): Promise<LineSummary[]> {
    const params = q.trim() ? `?q=${encodeURIComponent(q.trim())}` : '';
    return get<LineSummary[]>(`/lines${params}`);
  },

  /**
   * Fetch full line detail (shapes + stops).
   * Calls GET /api/lines/{id}
   */
  lineDetail(id: number): Promise<LineDetail> {
    return get<LineDetail>(`/lines/${id}`);
  },

  /**
   * List all neighbourhoods with per-relation line counts.
   * Calls GET /api/neighborhoods
   */
  listNeighborhoods(): Promise<NeighborhoodSummary[]> {
    return get<NeighborhoodSummary[]>('/neighborhoods');
  },

  /**
   * Get lines grouped by relation for a single neighbourhood.
   * Calls GET /api/neighborhoods/{id}/lines
   */
  neighborhoodLines(id: number): Promise<NeighborhoodLines> {
    return get<NeighborhoodLines>(`/neighborhoods/${id}/lines`);
  },

  /**
   * Get dataset metadata (attribution + last import timestamp).
   * Calls GET /api/meta
   */
  meta(): Promise<MetaInfo> {
    return get<MetaInfo>('/meta');
  },
};
