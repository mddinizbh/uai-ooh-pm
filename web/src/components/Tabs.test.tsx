import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tabs } from './Tabs';
import type { SidebarTab } from './Tabs';

// ── Helpers ───────────────────────────────────────────────────────────────────

function renderTabs(
  active: SidebarTab = 'neighborhood',
  onChange = vi.fn(),
) {
  const { rerender } = render(<Tabs active={active} onChange={onChange} />);
  return { onChange, rerender };
}

// ── Unit tests ────────────────────────────────────────────────────────────────

describe('Tabs', () => {
  describe('rendering', () => {
    it('renders a tablist with two tabs', () => {
      renderTabs();
      expect(screen.getByRole('tablist')).toBeInTheDocument();
      expect(screen.getAllByRole('tab')).toHaveLength(2);
    });

    it('renders "Por bairro" and "Por linha" buttons', () => {
      renderTabs();
      expect(screen.getByRole('tab', { name: 'Por bairro' })).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Por linha' })).toBeInTheDocument();
    });
  });

  describe('aria-selected state', () => {
    it('sets aria-selected="true" on the neighborhood tab when active="neighborhood"', () => {
      renderTabs('neighborhood');
      expect(screen.getByRole('tab', { name: 'Por bairro' })).toHaveAttribute(
        'aria-selected',
        'true',
      );
      expect(screen.getByRole('tab', { name: 'Por linha' })).toHaveAttribute(
        'aria-selected',
        'false',
      );
    });

    it('sets aria-selected="true" on the line tab when active="line"', () => {
      renderTabs('line');
      expect(screen.getByRole('tab', { name: 'Por linha' })).toHaveAttribute(
        'aria-selected',
        'true',
      );
      expect(screen.getByRole('tab', { name: 'Por bairro' })).toHaveAttribute(
        'aria-selected',
        'false',
      );
    });
  });

  describe('onChange interaction', () => {
    it('calls onChange("line") when clicking the "Por linha" tab', async () => {
      const onChange = vi.fn();
      renderTabs('neighborhood', onChange);
      await userEvent.click(screen.getByRole('tab', { name: 'Por linha' }));
      expect(onChange).toHaveBeenCalledWith('line');
      expect(onChange).toHaveBeenCalledTimes(1);
    });

    it('calls onChange("neighborhood") when clicking the "Por bairro" tab', async () => {
      const onChange = vi.fn();
      renderTabs('line', onChange);
      await userEvent.click(screen.getByRole('tab', { name: 'Por bairro' }));
      expect(onChange).toHaveBeenCalledWith('neighborhood');
      expect(onChange).toHaveBeenCalledTimes(1);
    });
  });

  describe('keyboard accessibility', () => {
    it('tab buttons are reachable via keyboard (Tab key)', async () => {
      renderTabs();
      await userEvent.tab();
      expect(screen.getByRole('tab', { name: 'Por bairro' })).toHaveFocus();
      await userEvent.tab();
      expect(screen.getByRole('tab', { name: 'Por linha' })).toHaveFocus();
    });

    it('activates onChange when pressing Enter on a tab', async () => {
      const onChange = vi.fn();
      renderTabs('neighborhood', onChange);
      const lineTab = screen.getByRole('tab', { name: 'Por linha' });
      lineTab.focus();
      await userEvent.keyboard('{Enter}');
      expect(onChange).toHaveBeenCalledWith('line');
    });

    it('activates onChange when pressing Space on a tab', async () => {
      const onChange = vi.fn();
      renderTabs('neighborhood', onChange);
      const lineTab = screen.getByRole('tab', { name: 'Por linha' });
      lineTab.focus();
      await userEvent.keyboard(' ');
      expect(onChange).toHaveBeenCalledWith('line');
    });
  });

  describe('integration — prop updates', () => {
    it('updates aria-selected when active prop changes from neighborhood to line', () => {
      const onChange = vi.fn();
      const { rerender } = render(<Tabs active="neighborhood" onChange={onChange} />);

      expect(screen.getByRole('tab', { name: 'Por bairro' })).toHaveAttribute(
        'aria-selected',
        'true',
      );

      rerender(<Tabs active="line" onChange={onChange} />);

      expect(screen.getByRole('tab', { name: 'Por linha' })).toHaveAttribute(
        'aria-selected',
        'true',
      );
      expect(screen.getByRole('tab', { name: 'Por bairro' })).toHaveAttribute(
        'aria-selected',
        'false',
      );
    });
  });
});
