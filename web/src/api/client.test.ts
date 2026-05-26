import { describe, it, expect } from 'vitest';
import { busLinesApi, ApiClientError } from './client';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { LINE_9400, LINE_9400_DETAIL } from '../test/handlers';

describe('busLinesApi', () => {
  describe('searchLines', () => {
    it('calls /api/lines with no params when query is empty', async () => {
      const lines = await busLinesApi.searchLines('');
      expect(lines).toHaveLength(2); // default fixture returns 2 lines
    });

    it('builds correct URL with encoded query parameter', async () => {
      let capturedUrl: string | undefined;
      server.use(
        http.get('/api/lines', ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([LINE_9400]);
        }),
      );

      await busLinesApi.searchLines('9400');
      expect(capturedUrl).toContain('q=9400');
    });

    it('trims whitespace from the query', async () => {
      let capturedUrl: string | undefined;
      server.use(
        http.get('/api/lines', ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([]);
        }),
      );

      await busLinesApi.searchLines('  9400  ');
      expect(capturedUrl).toContain('q=9400');
    });

    it('calls /api/lines without ?q= when query is only whitespace', async () => {
      let capturedUrl: string | undefined;
      server.use(
        http.get('/api/lines', ({ request }) => {
          capturedUrl = request.url;
          return HttpResponse.json([]);
        }),
      );

      await busLinesApi.searchLines('   ');
      expect(capturedUrl).not.toContain('q=');
    });

    it('returns parsed LineSummary array', async () => {
      const results = await busLinesApi.searchLines('9400');
      expect(results[0]).toMatchObject({
        id: LINE_9400.id,
        shortName: LINE_9400.shortName,
        longName: LINE_9400.longName,
      });
    });
  });

  describe('lineDetail', () => {
    it('calls /api/lines/{id} with the correct id', async () => {
      let capturedPath: string | undefined;
      server.use(
        http.get('/api/lines/:id', ({ request }) => {
          capturedPath = new URL(request.url).pathname;
          return HttpResponse.json(LINE_9400_DETAIL);
        }),
      );

      await busLinesApi.lineDetail(1);
      expect(capturedPath).toBe('/api/lines/1');
    });

    it('parses LineDetail response including shapes geometry', async () => {
      const detail = await busLinesApi.lineDetail(1);
      expect(detail.line.id).toBe(LINE_9400.id);
      expect(detail.shapes).toHaveLength(2);
      expect(detail.shapes[0].geometry.type).toBe('LineString');
    });

    it('throws ApiClientError with status 404 when line not found', async () => {
      let error: unknown;
      try {
        await busLinesApi.lineDetail(9999);
      } catch (e) {
        error = e;
      }
      expect(error).toBeInstanceOf(ApiClientError);
      expect((error as ApiClientError).status).toBe(404);
    });
  });

  describe('meta', () => {
    it('returns MetaInfo with attribution and lastImportedAt', async () => {
      const meta = await busLinesApi.meta();
      expect(meta.attribution).toBeTruthy();
      expect(meta.lastImportedAt).toBe('2026-05-25T10:30:00Z');
    });
  });

  describe('error handling', () => {
    it('throws ApiClientError with correct status on HTTP errors', async () => {
      server.use(
        http.get('/api/lines', () =>
          HttpResponse.json({ status: 500, message: 'Internal server error' }, { status: 500 }),
        ),
      );

      let error: unknown;
      try {
        await busLinesApi.searchLines();
      } catch (e) {
        error = e;
      }

      expect(error).toBeInstanceOf(ApiClientError);
      expect((error as ApiClientError).status).toBe(500);
      expect((error as ApiClientError).message).toBe('Internal server error');
    });

    it('uses fallback message when error body has no message field', async () => {
      server.use(
        http.get('/api/lines', () => new HttpResponse(null, { status: 503 })),
      );

      let error: unknown;
      try {
        await busLinesApi.searchLines();
      } catch (e) {
        error = e;
      }

      expect(error).toBeInstanceOf(ApiClientError);
      expect((error as ApiClientError).message).toContain('503');
    });
  });
});
