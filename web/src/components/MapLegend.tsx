/**
 * Static legend overlaid on the bottom-left of the map (Layout B).
 * Explains the visual distinction between the active (focused) line
 * and other selected (dimmed) lines.
 */
export function MapLegend() {
  return (
    <div role="region" className="map-legend" aria-label="Legenda de ênfase das linhas">
      <h4 className="map-legend__title">Ênfase</h4>
      <div className="map-legend__entry">
        <span className="map-legend__bar map-legend__bar--active" aria-hidden="true" />
        <span>Linha em foco</span>
      </div>
      <div className="map-legend__entry">
        <span className="map-legend__bar map-legend__bar--dimmed" aria-hidden="true" />
        <span>Outras selecionadas</span>
      </div>
    </div>
  );
}
