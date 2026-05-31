import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import { tokenStorage } from '@/shared/lib/tokenStorage';
import { logger, mask } from '@/shared/lib/logger';
import type { Role } from '@/shared/types/api';

/** Authenticated principal, hydrated from the login response or GET /api/me. */
export interface AuthUser {
  email: string;
  role: Role;
}

export interface AuthState {
  /** JWT access token, or null when anonymous. Mirrored to localStorage. */
  token: string | null;
  /** Principal details; null until login or /me hydration. */
  user: AuthUser | null;
}

interface Credentials {
  token: string;
  email: string;
  role: Role;
}

/**
 * Initial state read from storage so a reload keeps the session. `user` stays null
 * until GET /api/me hydrates it (see auth bootstrap, Task 12). In tests, configureStore
 * `preloadedState` overrides this.
 */
function getInitialAuthState(): AuthState {
  return { token: tokenStorage.get(), user: null };
}

const authSlice = createSlice({
  name: 'auth',
  initialState: getInitialAuthState,
  reducers: {
    /** Store the token + principal after a successful login (or /me hydration). */
    setCredentials(state, action: PayloadAction<Credentials>) {
      const { token, email, role } = action.payload;
      state.token = token;
      state.user = { email, role };
      tokenStorage.set(token);
      logger.info('[auth] credentials set', { email, role, token: mask(token) });
    },
    /** Hydrate only the principal (token already present) — used by /me bootstrap. */
    setUser(state, action: PayloadAction<AuthUser>) {
      state.user = action.payload;
      logger.debug('[auth] user hydrated', action.payload);
    },
    /** Clear the session everywhere (logout button, or 401 from the API). */
    logout(state) {
      state.token = null;
      state.user = null;
      tokenStorage.clear();
      logger.info('[auth] logout');
    },
  },
});

export const { setCredentials, setUser, logout } = authSlice.actions;
export const authReducer = authSlice.reducer;

/**
 * Selectors typed structurally (`{ auth: AuthState }`) rather than importing RootState,
 * which keeps this slice free of a store import cycle. RootState satisfies this shape.
 */
type AuthRootShape = { auth: AuthState };

export const selectToken = (state: AuthRootShape): string | null => state.auth.token;
export const selectIsAuthenticated = (state: AuthRootShape): boolean => Boolean(state.auth.token);
export const selectUser = (state: AuthRootShape): AuthUser | null => state.auth.user;
export const selectRole = (state: AuthRootShape): Role | null => state.auth.user?.role ?? null;
