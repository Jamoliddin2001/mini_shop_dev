import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type {
  BaseQueryFn,
  FetchArgs,
  FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react';
import { logout, type AuthState } from '@/features/auth/authSlice';
import { logger } from '@/shared/lib/logger';

/**
 * Single RTK Query API for the whole app. Feature slices attach their endpoints via
 * `baseApi.injectEndpoints` (see authApi). RTK Query is the server-cache layer; the
 * `auth` slice holds the client session — see plan "RTK Query vs thunks".
 */
const rawBaseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_BASE_URL,
  prepareHeaders: (headers, { getState }) => {
    // Inject the JWT on every request when present. State is read structurally to avoid
    // a store import cycle; RootState satisfies { auth: AuthState }.
    const token = (getState() as { auth: AuthState }).auth.token;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});

/**
 * Decorator over the base query (pattern: **Decorator** — wraps fetchBaseQuery to add a
 * cross-cutting concern). On 401 it clears the session; a route guard then redirects to
 * /login. There is NO token refresh: the backend issues a single short-lived access token
 * with no refresh endpoint, so re-auth means logging in again.
 */
const baseQueryWithReauth: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const result = await rawBaseQuery(args, api, extraOptions);

  if (import.meta.env.DEV) {
    const url = typeof args === 'string' ? args : args.url;
    logger.debug('[api] request', { url, status: result.error?.status ?? 'ok' });
  }

  if (result.error?.status === 401) {
    logger.warn('[api] 401 — clearing session');
    api.dispatch(logout());
  }

  return result;
};

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  // Declared up-front for future features; auth uses 'Me'.
  tagTypes: ['Products', 'Cart', 'Orders', 'Me'],
  endpoints: () => ({}),
});
