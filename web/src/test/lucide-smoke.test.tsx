/**
 * Smoke test: confirms that lucide-react icons can be imported and rendered
 * without throwing. This verifies the dependency is correctly installed and
 * tree-shakeable icons resolve in the test environment.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Loader2, X, ChevronDown, Bus } from 'lucide-react';

describe('lucide-react — import smoke tests', () => {
  it('renders <Loader2 /> without throwing', () => {
    render(<Loader2 aria-label="carregando" />);
    expect(screen.getByLabelText('carregando')).toBeInTheDocument();
  });

  it('renders <X /> without throwing', () => {
    render(<X aria-label="remover" />);
    expect(screen.getByLabelText('remover')).toBeInTheDocument();
  });

  it('renders <ChevronDown /> without throwing', () => {
    render(<ChevronDown aria-label="expandir" />);
    expect(screen.getByLabelText('expandir')).toBeInTheDocument();
  });

  it('renders <Bus /> without throwing', () => {
    render(<Bus aria-label="ônibus" />);
    expect(screen.getByLabelText('ônibus')).toBeInTheDocument();
  });
});
