import { X } from 'lucide-react';
import type { LineDetail } from '../api/types';
import { lineColor } from '../utils/mapLayers';

export interface SelectedLinesBarProps {
  /** Currently selected lines, keyed by line id (same Map App holds). */
  selectedLines: Map<number, LineDetail>;
  /** Remove a line from the map + selection. */
  onDeselectLine: (id: number) => void;
}

/**
 * Chip bar overlaid on the top-left of the map (Layout B).
 * Renders one chip per selected line: color swatch + short name + remove button.
 * Returns null when no lines are selected.
 */
export function SelectedLinesBar({
  selectedLines,
  onDeselectLine,
}: SelectedLinesBarProps) {
  if (selectedLines.size === 0) return null;

  return (
    <div role="region" className="map__chipbar" aria-label="Linhas no mapa">
      {Array.from(selectedLines.entries()).map(([id, detail]) => {
        const shortName = detail.line.shortName;
        const color = lineColor(id);
        return (
          <span key={id} className="chip">
            <span
              className="chip__swatch"
              style={{ background: color }}
              aria-hidden="true"
            />
            <span className="chip__name">{shortName}</span>
            <button
              type="button"
              className="chip__remove"
              aria-label={`Remover linha ${shortName}`}
              onClick={() => onDeselectLine(id)}
            >
              <X size={12} aria-hidden />
            </button>
          </span>
        );
      })}
    </div>
  );
}
