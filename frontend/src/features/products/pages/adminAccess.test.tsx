import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import AppLayout from '@/app/layout/AppLayout';
import RoleRoute from '@/features/auth/guards/RoleRoute';
import AdminProductsPage from '@/features/products/pages/AdminProductsPage';

const user = { auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' as const } } };
const admin = { auth: { token: 'jwt', user: { email: 'a@shop.local', role: 'ADMIN' as const } } };

afterEach(() => vi.unstubAllGlobals());

describe('Admin UI hiding by role', () => {
  it('hides the "Админка" nav link from a regular user', () => {
    installFetch(() => jsonResponse(categoriesOrMe()));
    renderWithProviders(<AppLayout />, { preloadedState: user });

    expect(screen.queryByText('Админка')).not.toBeInTheDocument();
  });

  it('shows the "Админка" nav link to an admin', () => {
    installFetch(() => jsonResponse(categoriesOrMe()));
    renderWithProviders(<AppLayout />, { preloadedState: admin });

    expect(screen.getByText('Админка')).toBeInTheDocument();
  });

  it('redirects a USER away from the admin route (server is the real authority)', () => {
    installFetch(() => jsonResponse(categoriesOrMe()));
    renderWithProviders(
      <Routes>
        <Route path="/" element={<p>home page</p>} />
        <Route
          path="/admin/products"
          element={
            <RoleRoute role="ADMIN">
              <AdminProductsPage />
            </RoleRoute>
          }
        />
      </Routes>,
      { route: '/admin/products', preloadedState: user },
    );

    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(screen.queryByText('Управление товарами')).not.toBeInTheDocument();
  });
});

// AppLayout's bootstrap won't fetch (user is preloaded); this is just a safe default body.
function categoriesOrMe() {
  return { email: 'a@shop.local', role: 'ADMIN' };
}
