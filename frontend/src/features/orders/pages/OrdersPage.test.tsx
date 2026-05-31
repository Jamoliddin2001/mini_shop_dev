import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import OrdersPage from '@/features/orders/pages/OrdersPage';

const authed = { auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' as const } } };

function OrdersRoutes() {
  return (
    <Routes>
      <Route path="/orders" element={<OrdersPage />} />
      <Route path="/orders/:id" element={<p>detail page</p>} />
    </Routes>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('OrdersPage', () => {
  it('renders the order history with status and total', async () => {
    installFetch(() =>
      jsonResponse({
        content: [{ id: 7, status: 'NEW', totalAmount: 50, createdAt: '2026-05-30T10:00:00Z' }],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    renderWithProviders(<OrdersRoutes />, { route: '/orders', preloadedState: authed });

    expect(await screen.findByText('#7')).toBeInTheDocument();
    expect(screen.getByText('Новый')).toBeInTheDocument();
  });

  it('shows the empty state when there are no orders', async () => {
    installFetch(() =>
      jsonResponse({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }),
    );
    renderWithProviders(<OrdersRoutes />, { route: '/orders', preloadedState: authed });

    expect(await screen.findByText('Заказов пока нет.')).toBeInTheDocument();
  });
});
