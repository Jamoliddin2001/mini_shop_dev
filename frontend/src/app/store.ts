import { combineReducers, configureStore } from '@reduxjs/toolkit';
import { baseApi } from '@/app/api/baseApi';
import { authReducer } from '@/features/auth/authSlice';
import { logger } from '@/shared/lib/logger';

const rootReducer = combineReducers({
  auth: authReducer,
  [baseApi.reducerPath]: baseApi.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;

/**
 * Store factory. The app builds one instance (below); tests build isolated stores with
 * `preloadedState` and pass them to `renderWithProviders`. RTK Query middleware is
 * required for caching, invalidation and refetch behaviour.
 */
export function makeStore(preloadedState?: Partial<RootState>) {
  return configureStore({
    reducer: rootReducer,
    preloadedState,
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(baseApi.middleware),
  });
}

export const store = makeStore();

logger.debug('[store] initialized', { hasToken: Boolean(store.getState().auth.token) });

export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore['dispatch'];
