import { describe, it, expect } from 'vitest';
import { makeStore } from '@/app/store';
import {
  setCredentials,
  setUser,
  logout,
  selectIsAuthenticated,
  selectRole,
} from '@/features/auth/authSlice';

describe('authSlice', () => {
  it('setCredentials stores token + user and persists the token to localStorage', () => {
    const store = makeStore();

    store.dispatch(setCredentials({ token: 'jwt-123', email: 'a@shop.local', role: 'USER' }));

    const state = store.getState();
    expect(state.auth.token).toBe('jwt-123');
    expect(state.auth.user).toEqual({ email: 'a@shop.local', role: 'USER' });
    expect(selectIsAuthenticated(state)).toBe(true);
    expect(selectRole(state)).toBe('USER');
    expect(window.localStorage.getItem('shop_token')).toBe('jwt-123');
  });

  it('logout clears state and removes the token from localStorage', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ token: 'jwt-123', email: 'a@shop.local', role: 'ADMIN' }));

    store.dispatch(logout());

    const state = store.getState();
    expect(state.auth.token).toBeNull();
    expect(state.auth.user).toBeNull();
    expect(selectIsAuthenticated(state)).toBe(false);
    expect(window.localStorage.getItem('shop_token')).toBeNull();
  });

  it('setUser hydrates the principal without touching the token', () => {
    const store = makeStore({ auth: { token: 'jwt-123', user: null } });

    store.dispatch(setUser({ email: 'me@shop.local', role: 'ADMIN' }));

    const state = store.getState();
    expect(state.auth.token).toBe('jwt-123');
    expect(state.auth.user).toEqual({ email: 'me@shop.local', role: 'ADMIN' });
  });

  it('bootstraps the initial token from localStorage', () => {
    window.localStorage.setItem('shop_token', 'persisted-jwt');

    const store = makeStore();

    expect(store.getState().auth.token).toBe('persisted-jwt');
    expect(selectIsAuthenticated(store.getState())).toBe(true);
  });
});
