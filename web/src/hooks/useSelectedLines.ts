import { useState, useCallback } from 'react';
import type { LineDetail } from '../api/types';

export interface SelectedLinesState {
  /** Ordered map of selected lines: lineId → LineDetail */
  selectedLines: Map<number, LineDetail>;
  /** ID of the most recently selected/focused line */
  activeLineId: number | null;
  isSelected: (id: number) => boolean;
  /** Select a line (marks it active). No-op if already selected. */
  select: (detail: LineDetail) => void;
  /** Deselect a line by ID. If it was active, clears activeLineId. */
  deselect: (id: number) => void;
  /** Toggle selection for a line. Marks the line active on selection. */
  toggle: (detail: LineDetail) => void;
  /** Clear all selected lines */
  clearAll: () => void;
}

export function useSelectedLines(): SelectedLinesState {
  const [selectedLines, setSelectedLines] = useState<Map<number, LineDetail>>(
    new Map(),
  );
  const [activeLineId, setActiveLineId] = useState<number | null>(null);

  const isSelected = useCallback(
    (id: number) => selectedLines.has(id),
    [selectedLines],
  );

  const select = useCallback((detail: LineDetail) => {
    const id = detail.line.id;
    setSelectedLines((prev) => {
      if (prev.has(id)) return prev; // already selected — just activate
      return new Map(prev).set(id, detail);
    });
    setActiveLineId(id);
  }, []);

  const deselect = useCallback(
    (id: number) => {
      setSelectedLines((prev) => {
        const next = new Map(prev);
        next.delete(id);
        return next;
      });
      setActiveLineId((prev) => (prev === id ? null : prev));
    },
    [],
  );

  const toggle = useCallback((detail: LineDetail) => {
    const id = detail.line.id;
    setSelectedLines((prev) => {
      const next = new Map(prev);
      if (next.has(id)) {
        next.delete(id);
        return next;
      }
      return next.set(id, detail);
    });
    setActiveLineId((prev) => {
      // If toggling off, clear active; if toggling on, set active
      return prev === id ? null : id;
    });
  }, []);

  const clearAll = useCallback(() => {
    setSelectedLines(new Map());
    setActiveLineId(null);
  }, []);

  return { selectedLines, activeLineId, isSelected, select, deselect, toggle, clearAll };
}
