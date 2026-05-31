import { describe, it, expect, vi, afterEach } from 'vitest';
import { makeStore } from '@/app/store';
import { authApi } from '@/features/auth/authApi';

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('baseQueryWithReauth', () => {
  it('clears the session when the API returns 401', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ message: 'Unauthorized' }, 401)),
    );
    const store = makeStore({ auth: { token: 'jwt-123', user: { email: 'a@shop.local', role: 'USER' } } });

    await store.dispatch(authApi.endpoints.getMe.initiate());

    expect(store.getState().auth.token).toBeNull();
    expect(store.getState().auth.user).toBeNull();
  });

  it('keeps the session and sends the Bearer token on success', async () => {
    const fetchMock = vi.fn<typeof fetch>(async () =>
      jsonResponse({ email: 'a@shop.local', role: 'USER' }, 200),
    );
    vi.stubGlobal('fetch', fetchMock);
    const store = makeStore({ auth: { token: 'jwt-123', user: null } });

    await store.dispatch(authApi.endpoints.getMe.initiate());

    expect(store.getState().auth.token).toBe('jwt-123');
    // Authorization header injected by prepareHeaders. RTK Query calls fetch with a Request.
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer jwt-123');
  });
});
