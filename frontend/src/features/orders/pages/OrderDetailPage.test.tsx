import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Routes, Route, type InitialEntry } from 'react-router-dom';
import { makeStore, type RootState } from '@/app/store';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import OrderDetailPage from '@/features/orders/pages/OrderDetailPage';

const order = {
  id: 7,
  status: 'NEW' as const,
  totalAmount: 50,
  createdAt: '2026-05-30T10:00:00Z',
  items: [{ productId: 1, productName: 'Wireless Mouse', unitPrice: 25, quantity: 2, lineTotal: 50 }],
};

const authed = { auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' as const } } };

/** Renders OrderDetailPage at /orders/7 with a router entry that may carry location state. */
function renderDetail(entry: InitialEntry, preloadedState: Partial<RootState>) {
  return render(
    <Provider store={makeStore(preloadedState)}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('OrderDetailPage', () => {
  it('renders the order lines and status', async () => {
    installFetch(() => jsonResponse(order));
    renderWithProviders(
      <Routes>
        <Route path="/orders/:id" element={<OrderDetailPage />} />
      </Routes>,
      { route: '/orders/7', preloadedState: authed },
    );

    expect(await screen.findByText('Wireless Mouse')).toBeInTheDocument();
    expect(screen.getByText('Новый')).toBeInTheDocument();
    expect(screen.queryByText(/Заказ успешно оформлен/)).not.toBeInTheDocument();
  });

  it('shows the confirmation banner when arriving from checkout (justCreated)', async () => {
    installFetch(() => jsonResponse(order));
    renderDetail({ pathname: '/orders/7', state: { justCreated: true } }, authed);

    expect(await screen.findByText(/Заказ успешно оформлен/)).toBeInTheDocument();
  });

  it('shows a message and makes no request for a non-numeric id', () => {
    const fetchMock = installFetch(() => jsonResponse(order));
    renderDetail({ pathname: '/orders/abc' }, authed);

    expect(screen.getByText('Некорректный номер заказа.')).toBeInTheDocument();
    const orderCalls = fetchMock.mock.calls.filter((c) =>
      (c[0] as Request).url.includes('/orders/'),
    );
    expect(orderCalls).toHaveLength(0);
  });
});
