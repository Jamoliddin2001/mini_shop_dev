import type { ReactElement, ReactNode } from 'react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { render, type RenderOptions } from '@testing-library/react';
import { makeStore, type AppStore, type RootState } from '@/app/store';

/**
 * Renders a component inside the two providers every feature needs: the Redux store and
 * the router. Follows the official RTK testing pattern — pass `preloadedState` to seed
 * the store, or inject a fully built `store` when a test needs to dispatch/inspect it.
 */
interface ProviderOptions extends Omit<RenderOptions, 'wrapper'> {
  preloadedState?: Partial<RootState>;
  store?: AppStore;
  /** Initial router entry, e.g. '/login'. */
  route?: string;
}

export function renderWithProviders(
  ui: ReactElement,
  { preloadedState, store = makeStore(preloadedState), route = '/', ...options }: ProviderOptions = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <Provider store={store}>
        <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
      </Provider>
    );
  }

  return { store, ...render(ui, { wrapper: Wrapper, ...options }) };
}
