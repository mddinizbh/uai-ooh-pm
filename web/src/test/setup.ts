import '@testing-library/jest-dom';
import { beforeAll, afterEach, afterAll } from 'vitest';
import { server } from './server';

// Start the MSW server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

// Reset handlers to defaults after each test
afterEach(() => server.resetHandlers());

// Clean up after all tests
afterAll(() => server.close());
