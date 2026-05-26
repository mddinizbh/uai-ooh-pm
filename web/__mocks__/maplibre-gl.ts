/**
 * Manual Vitest mock for maplibre-gl.
 *
 * MapLibre requires WebGL which is not available in jsdom.
 * This mock replaces the module with a lightweight in-memory implementation.
 *
 * Usage in test files:
 *   vi.mock('maplibre-gl');
 *   import { Map as MapMock } from 'maplibre-gl';
 *   const instance = MapMock.lastInstance; // access the map created by the component
 */
import { vi } from 'vitest';

export class Map {
  /** The most recently constructed Map instance — use in tests to inspect calls. */
  static lastInstance: Map;

  private _eventHandlers: Record<string, ((...args: unknown[]) => void)[]> = {};

  getSource = vi.fn().mockReturnValue(null);
  addSource = vi.fn();
  removeSource = vi.fn();
  getLayer = vi.fn().mockReturnValue(null);
  addLayer = vi.fn();
  removeLayer = vi.fn();
  setPaintProperty = vi.fn();
  setLayoutProperty = vi.fn();
  setStyle = vi.fn();
  fitBounds = vi.fn();
  remove = vi.fn();
  loaded = vi.fn().mockReturnValue(true);
  isStyleLoaded = vi.fn().mockReturnValue(true);
  off = vi.fn().mockReturnThis();

  constructor(_options?: unknown) {
    // Reset all mocks on construction so each test starts clean
    this.getSource = vi.fn().mockReturnValue(null);
    this.addSource = vi.fn();
    this.removeSource = vi.fn();
    this.getLayer = vi.fn().mockReturnValue(null);
    this.addLayer = vi.fn();
    this.removeLayer = vi.fn();
    this.setPaintProperty = vi.fn();
    this.setLayoutProperty = vi.fn();
    this.setStyle = vi.fn();
    this.fitBounds = vi.fn();
    this.remove = vi.fn();
    this.loaded = vi.fn().mockReturnValue(true);
    this.isStyleLoaded = vi.fn().mockReturnValue(true);
    this.off = vi.fn().mockReturnThis();
    this._eventHandlers = {};

    Map.lastInstance = this;
  }

  on(event: string, callback: (...args: unknown[]) => void): this {
    if (!this._eventHandlers[event]) {
      this._eventHandlers[event] = [];
    }
    this._eventHandlers[event].push(callback);

    // Fire 'load' synchronously so tests don't need async handling
    if (event === 'load') {
      callback();
    }

    return this;
  }

  /** Manually fire an event — useful for simulating map interactions in tests. */
  _fire(event: string, ...args: unknown[]): void {
    this._eventHandlers[event]?.forEach((cb) => cb(...args));
  }
}

export class NavigationControl {
  onAdd = vi.fn();
  onRemove = vi.fn();
}

export class AttributionControl {
  onAdd = vi.fn();
  onRemove = vi.fn();
}

export default { Map, NavigationControl, AttributionControl };
