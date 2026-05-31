import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import PrivateRoute from '@/features/auth/guards/PrivateRoute';
import RoleRoute from '@/features/auth/guards/RoleRoute';

function TestRoutes() {
  return (
    <Routes>
      <Route path="/" element={<p>home page</p>} />
      <Route path="/login" element={<p>login page</p>} />
      <Route
        path="/cart"
        element={
          <PrivateRoute>
            <p>cart content</p>
          </PrivateRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <RoleRoute role="ADMIN">
            <p>admin content</p>
          </RoleRoute>
        }
      />
    </Routes>
  );
}

describe('PrivateRoute', () => {
  it('redirects an anonymous user to /login', () => {
    renderWithProviders(<TestRoutes />, { route: '/cart' });
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('cart content')).not.toBeInTheDocument();
  });

  it('renders the protected content for an authenticated user', () => {
    renderWithProviders(<TestRoutes />, {
      route: '/cart',
      preloadedState: { auth: { token: 'jwt', user: { email: 'a@shop.local', role: 'USER' } } },
    });
    expect(screen.getByText('cart content')).toBeInTheDocument();
  });
});

describe('RoleRoute', () => {
  it('redirects a USER away from an ADMIN route to home', () => {
    renderWithProviders(<TestRoutes />, {
      route: '/admin',
      preloadedState: { auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' } } },
    });
    expect(screen.getByText('home page')).toBeInTheDocument();
    expect(screen.queryByText('admin content')).not.toBeInTheDocument();
  });

  it('renders the ADMIN content for an admin user', () => {
    renderWithProviders(<TestRoutes />, {
      route: '/admin',
      preloadedState: { auth: { token: 'jwt', user: { email: 'admin@shop.local', role: 'ADMIN' } } },
    });
    expect(screen.getByText('admin content')).toBeInTheDocument();
  });
});
