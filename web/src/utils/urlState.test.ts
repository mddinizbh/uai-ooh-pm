/**
 * Unit tests for URL view-state encoder / decoder.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { readUrlState, writeUrlState } from './urlState';

// ── Helpers ───────────────────────────────────────────────────────────────────

function setSearch(search: string) {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { ...window.location, search, pathname: '/' },
  });
}

// ── readUrlState ──────────────────────────────────────────────────────────────

describe('readUrlState', () => {
  afterEach(() => {
    setSearch('');
  });

  it('returns empty state when URL has no params', () => {
    setSearch('');
    expect(readUrlState()).toEqual({ neighborhoodId: null, lineIds: [] });
  });

  it('parses neighborhoodId from ?n=42', () => {
    setSearch('?n=42');
    expect(readUrlState()).toEqual({ neighborhoodId: 42, lineIds: [] });
  });

  it('parses lineIds from ?lines=1,2,3', () => {
    setSearch('?lines=1,2,3');
    expect(readUrlState()).toEqual({ neighborhoodId: null, lineIds: [1, 2, 3] });
  });

  it('parses both n and lines together', () => {
    setSearch('?n=5&lines=10,20');
    expect(readUrlState()).toEqual({ neighborhoodId: 5, lineIds: [10, 20] });
  });

  it('ignores non-positive or NaN line IDs', () => {
    setSearch('?lines=1,abc,0,-3,5');
    expect(readUrlState().lineIds).toEqual([1, 5]);
  });

  it('returns null neighborhoodId for non-positive n', () => {
    setSearch('?n=0');
    expect(readUrlState().neighborhoodId).toBeNull();
  });

  it('returns null neighborhoodId for non-numeric n', () => {
    setSearch('?n=abc');
    expect(readUrlState().neighborhoodId).toBeNull();
  });
});

// ── writeUrlState ─────────────────────────────────────────────────────────────

describe('writeUrlState', () => {
  const replaceStateSpy = vi.spyOn(window.history, 'replaceState');

  beforeEach(() => {
    replaceStateSpy.mockClear();
  });

  it('writes neighborhoodId and lineIds to URL', () => {
    writeUrlState({ neighborhoodId: 5, lineIds: [1, 2, 3] });
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '?n=5&lines=1%2C2%2C3');
  });

  it('writes only n when lineIds is empty', () => {
    writeUrlState({ neighborhoodId: 10, lineIds: [] });
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '?n=10');
  });

  it('writes only lines when neighborhoodId is null', () => {
    writeUrlState({ neighborhoodId: null, lineIds: [7, 8] });
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '?lines=7%2C8');
  });

  it('clears URL to pathname when both are empty', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...window.location, pathname: '/test' },
    });
    writeUrlState({ neighborhoodId: null, lineIds: [] });
    expect(replaceStateSpy).toHaveBeenCalledWith(null, '', '/test');
  });
});
