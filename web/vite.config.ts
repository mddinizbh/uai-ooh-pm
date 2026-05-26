import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false, // Don't process CSS imports in tests (maplibre-gl/dist/maplibre-gl.css)
    coverage: {
      provider: 'v8',
      thresholds: {
        lines: 80,
      },
      exclude: [
        'node_modules/**',
        'src/test/**',
        'src/main.tsx',
        '**/*.test.{ts,tsx}',
        '**/*.d.ts',
        '__mocks__/**',
      ],
      include: ['src/**/*.{ts,tsx}'],
    },
  },
});
