import { vi } from 'vitest';

/**
 * Shared test helpers for stubbing `fetch` (RTK Query's transport). Mirrors the pattern
 * established in baseApi.test.ts — lifted here so every feature test reuses it instead of
 * redefining `jsonResponse` (the project forbids duplication).
 */

/** Build a JSON `Response`; pass status 204 for an empty body. */
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

type FetchHandler = (req: Request) => Response | Promise<Response>;

/**
 * Stub global `fetch` with a handler that receives the RTK Query `Request` (so the test
 * can branch on `req.url` / `req.method`). Returns the mock for call assertions.
 * Remember `afterEach(() => vi.unstubAllGlobals())`.
 */
export function installFetch(handler: FetchHandler) {
  const mock = vi.fn(async (input: Request | string | URL) => {
    const req = input instanceof Request ? input : new Request(String(input));
    return handler(req);
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}
