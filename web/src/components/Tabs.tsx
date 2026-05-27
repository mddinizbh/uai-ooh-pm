export type SidebarTab = 'neighborhood' | 'line';

export interface TabsProps {
  active: SidebarTab;
  onChange: (tab: SidebarTab) => void;
}

export function Tabs({ active, onChange }: TabsProps) {
  return (
    <div className="tabs" role="tablist">
      <button
        role="tab"
        aria-selected={active === 'neighborhood'}
        className={`tabs__btn${active === 'neighborhood' ? ' tabs__btn--active' : ''}`}
        onClick={() => onChange('neighborhood')}
      >
        Por bairro
      </button>
      <button
        role="tab"
        aria-selected={active === 'line'}
        className={`tabs__btn${active === 'line' ? ' tabs__btn--active' : ''}`}
        onClick={() => onChange('line')}
      >
        Por linha
      </button>
    </div>
  );
}
