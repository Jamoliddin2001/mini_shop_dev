import { logger } from '@/shared/lib/logger';

/**
 * Persists the JWT access token in localStorage so the session survives a page reload.
 *
 * Trade-off (documented in the plan): localStorage is readable by JS, so it is exposed
 * to XSS. Acceptable for this mini-shop given React's default escaping and a short token
 * TTL (1h). A production hardening path is httpOnly + SameSite cookies on the backend.
 *
 * All access is wrapped in try/catch: storage can be unavailable (private mode, disabled
 * cookies) and must never crash the app.
 */
const TOKEN_KEY = 'shop_token';

export const tokenStorage = {
  get(): string | null {
    try {
      return window.localStorage.getItem(TOKEN_KEY);
    } catch (err) {
      logger.warn('[tokenStorage] read failed', err);
      return null;
    }
  },

  set(token: string): void {
    try {
      window.localStorage.setItem(TOKEN_KEY, token);
    } catch (err) {
      logger.warn('[tokenStorage] write failed', err);
    }
  },

  clear(): void {
    try {
      window.localStorage.removeItem(TOKEN_KEY);
    } catch (err) {
      logger.warn('[tokenStorage] clear failed', err);
    }
  },
};
