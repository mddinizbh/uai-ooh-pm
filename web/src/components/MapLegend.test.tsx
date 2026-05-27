import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MapLegend } from './MapLegend';

describe('MapLegend', () => {
  describe('rendering', () => {
    it('renders the legend with an accessible label', () => {
      render(<MapLegend />);
      expect(
        screen.getByRole('region', { name: /legenda/i }),
      ).toBeInTheDocument();
    });

    it('renders the "Ênfase" heading', () => {
      render(<MapLegend />);
      expect(screen.getByText('Ênfase')).toBeInTheDocument();
    });

    it('renders the active line entry "Linha em foco"', () => {
      render(<MapLegend />);
      expect(screen.getByText('Linha em foco')).toBeInTheDocument();
    });

    it('renders the dimmed line entry "Outras selecionadas"', () => {
      render(<MapLegend />);
      expect(screen.getByText('Outras selecionadas')).toBeInTheDocument();
    });

    it('renders the active bar with the correct CSS class', () => {
      render(<MapLegend />);
      const activeBars = document.querySelectorAll('.map-legend__bar--active');
      expect(activeBars.length).toBeGreaterThan(0);
    });

    it('renders the dimmed bar with the correct CSS class', () => {
      render(<MapLegend />);
      const dimmedBars = document.querySelectorAll('.map-legend__bar--dimmed');
      expect(dimmedBars.length).toBeGreaterThan(0);
    });

    it('renders exactly two legend entries', () => {
      render(<MapLegend />);
      const entries = document.querySelectorAll('.map-legend__entry');
      expect(entries).toHaveLength(2);
    });
  });
});
