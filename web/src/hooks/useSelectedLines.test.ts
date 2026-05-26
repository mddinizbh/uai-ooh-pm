import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSelectedLines } from './useSelectedLines';
import type { LineDetail } from '../api/types';

// ── Fixtures ──────────────────────────────────────────────────────────────────

const makeDetail = (id: number, shortName = `${id}00`): LineDetail => ({
  line: { id, shortName, longName: `Linha ${shortName} - Centro` },
  shapes: [],
  stops: [],
});

const DETAIL_1 = makeDetail(1, '9400');
const DETAIL_2 = makeDetail(2, '9401');

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('useSelectedLines', () => {
  it('starts with no selected lines and no active line', () => {
    const { result } = renderHook(() => useSelectedLines());
    expect(result.current.selectedLines.size).toBe(0);
    expect(result.current.activeLineId).toBeNull();
  });

  describe('select', () => {
    it('adds a line to selectedLines', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));

      expect(result.current.selectedLines.has(1)).toBe(true);
      expect(result.current.selectedLines.get(1)).toEqual(DETAIL_1);
    });

    it('sets the selected line as active', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));

      expect(result.current.activeLineId).toBe(1);
    });

    it('is a no-op for selectedLines when already selected (keeps existing)', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      const mapBefore = result.current.selectedLines;

      act(() => result.current.select(DETAIL_1)); // select again
      // Map reference unchanged — no new Map was created
      expect(result.current.selectedLines).toBe(mapBefore);
    });

    it('selects multiple lines independently', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.select(DETAIL_2));

      expect(result.current.selectedLines.size).toBe(2);
      expect(result.current.activeLineId).toBe(2);
    });
  });

  describe('deselect', () => {
    it('removes a line from selectedLines', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.deselect(1));

      expect(result.current.selectedLines.has(1)).toBe(false);
    });

    it('clears activeLineId when the active line is deselected', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.deselect(1));

      expect(result.current.activeLineId).toBeNull();
    });

    it('keeps activeLineId when a non-active line is deselected', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.select(DETAIL_2)); // DETAIL_2 is now active

      act(() => result.current.deselect(1)); // deselect non-active

      expect(result.current.activeLineId).toBe(2);
    });
  });

  describe('toggle', () => {
    it('selects a line when it is not selected', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.toggle(DETAIL_1));

      expect(result.current.selectedLines.has(1)).toBe(true);
      expect(result.current.activeLineId).toBe(1);
    });

    it('deselects a line when it is already selected', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.toggle(DETAIL_1));

      expect(result.current.selectedLines.has(1)).toBe(false);
      expect(result.current.activeLineId).toBeNull();
    });
  });

  describe('isSelected', () => {
    it('returns false for unselected lines', () => {
      const { result } = renderHook(() => useSelectedLines());
      expect(result.current.isSelected(99)).toBe(false);
    });

    it('returns true for selected lines', () => {
      const { result } = renderHook(() => useSelectedLines());
      act(() => result.current.select(DETAIL_1));
      expect(result.current.isSelected(1)).toBe(true);
    });
  });

  describe('clearAll', () => {
    it('removes all selected lines', () => {
      const { result } = renderHook(() => useSelectedLines());

      act(() => result.current.select(DETAIL_1));
      act(() => result.current.select(DETAIL_2));
      act(() => result.current.clearAll());

      expect(result.current.selectedLines.size).toBe(0);
      expect(result.current.activeLineId).toBeNull();
    });
  });
});
