/**
 * URL view-state encoder / decoder.
 *
 * The shareable URL format is:
 *   ?n=<neighborhoodId>&lines=<id1>,<id2>,...
 *
 * Both parameters are optional. `lines` is a comma-separated list of line IDs.
 * This module is intentionally side-effect-free (pure functions + one hook) so
 * it is easy to test without mocking browser globals.
 */

// ── Types ─────────────────────────────────────────────────────────────────────

export interface UrlViewState {
  /** Selected neighbourhood ID, or null if none */
  neighborhoodId: number | null;
  /** Currently selected line IDs (order preserved) */
  lineIds: number[];
}

// ── Pure encode / decode ──────────────────────────────────────────────────────

/**
 * Read the current view state from `window.location.search`.
 * Returns a safe default if the params are absent or malformed.
 */
export function readUrlState(): UrlViewState {
  const params = new URLSearchParams(window.location.search);

  const nRaw = params.get('n');
  const neighborhoodId =
    nRaw != null && nRaw !== ''
      ? (() => {
          const parsed = parseInt(nRaw, 10);
          return isFinite(parsed) && parsed > 0 ? parsed : null;
        })()
      : null;

  const linesRaw = params.get('lines');
  const lineIds = linesRaw
    ? linesRaw
        .split(',')
        .map((s) => parseInt(s.trim(), 10))
        .filter((n) => isFinite(n) && n > 0)
    : [];

  return { neighborhoodId, lineIds };
}

/**
 * Write the view state to the browser URL without triggering a navigation.
 * Clears both params when the state is empty.
 */
export function writeUrlState(state: UrlViewState): void {
  const params = new URLSearchParams();

  if (state.neighborhoodId != null) {
    params.set('n', String(state.neighborhoodId));
  }
  if (state.lineIds.length > 0) {
    params.set('lines', state.lineIds.join(','));
  }

  const search = params.toString();
  const newUrl = search ? `?${search}` : window.location.pathname;
  window.history.replaceState(null, '', newUrl);
}
