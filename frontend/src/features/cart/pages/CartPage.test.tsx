import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import CartPage from '@/features/cart/pages/CartPage';

const cart = {
  items: [{ productId: 1, productName: 'Wireless Mouse', unitPrice: 25, quantity: 2, lineTotal: 50 }],
  totalAmount: 50,
  totalItems: 2,
};

function CartRoutes() {
  return (
    <Routes>
      <Route path="/cart" element={<CartPage />} />
      <Route path="/orders/:id" element={<p>order confirmation</p>} />
    </Routes>
  );
}

const authed = { auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' as const } } };

afterEach(() => vi.unstubAllGlobals());

describe('CartPage', () => {
  it('renders the cart lines and total', async () => {
    installFetch(() => jsonResponse(cart));
    renderWithProviders(<CartRoutes />, { route: '/cart', preloadedState: authed });

    expect(await screen.findByText('Wireless Mouse')).toBeInTheDocument();
    expect(screen.getByText(/Итого:/)).toHaveTextContent('50');
  });

  it('shows the empty state for an empty cart', async () => {
    installFetch(() => jsonResponse({ items: [], totalAmount: 0, totalItems: 0 }));
    renderWithProviders(<CartRoutes />, { route: '/cart', preloadedState: authed });

    expect(await screen.findByText('Корзина пуста.')).toBeInTheDocument();
  });

  it('removes a line via DELETE /cart/items/:id', async () => {
    const fetchMock = installFetch(() => jsonResponse(cart));
    const user = userEvent.setup();
    renderWithProviders(<CartRoutes />, { route: '/cart', preloadedState: authed });

    await user.click(await screen.findByRole('button', { name: 'Удалить' }));

    await waitFor(() => {
      const deleted = fetchMock.mock.calls.some(
        (c) => (c[0] as Request).method === 'DELETE' && (c[0] as Request).url.endsWith('/cart/items/1'),
      );
      expect(deleted).toBe(true);
    });
  });

  it('places an order and navigates to the confirmation', async () => {
    installFetch((req) => {
      if (req.method === 'POST' && req.url.endsWith('/orders')) {
        return jsonResponse(
          { id: 7, status: 'NEW', totalAmount: 50, createdAt: '2026-05-30T10:00:00Z', items: cart.items },
          201,
        );
      }
      return jsonResponse(cart);
    });
    const user = userEvent.setup();
    renderWithProviders(<CartRoutes />, { route: '/cart', preloadedState: authed });

    await user.click(await screen.findByRole('button', { name: 'Оформить заказ' }));

    expect(await screen.findByText('order confirmation')).toBeInTheDocument();
  });

  it('shows the server message when checkout fails', async () => {
    installFetch((req) => {
      if (req.method === 'POST' && req.url.endsWith('/orders')) {
        return jsonResponse(
          { timestamp: 't', status: 400, error: 'Bad Request', message: 'Cart is empty', path: '/api/orders' },
          400,
        );
      }
      return jsonResponse(cart);
    });
    const user = userEvent.setup();
    renderWithProviders(<CartRoutes />, { route: '/cart', preloadedState: authed });

    await user.click(await screen.findByRole('button', { name: 'Оформить заказ' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cart is empty');
  });
});
