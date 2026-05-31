import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import AdminProductsPage from '@/features/products/pages/AdminProductsPage';

const product = {
  id: 1,
  name: 'Keyboard',
  description: null,
  price: 89.5,
  imageUrl: null,
  categoryId: 1,
  categoryName: 'Electronics',
  createdAt: '2026-05-01T10:00:00Z',
};
const page = { content: [product], page: 0, size: 10, totalElements: 1, totalPages: 1 };
const categories = [{ id: 1, name: 'Electronics' }];

const admin = { auth: { token: 'jwt', user: { email: 'admin@shop.local', role: 'ADMIN' as const } } };

/** Routes the product/category reads; `onWrite` handles POST/PUT/DELETE. */
function routeAdmin(onWrite: (req: Request) => Response) {
  return installFetch((req) => {
    const path = new URL(req.url).pathname;
    if (req.method === 'GET' && path.endsWith('/categories')) return jsonResponse(categories);
    if (req.method === 'GET' && path.endsWith('/products')) return jsonResponse(page);
    return onWrite(req);
  });
}

afterEach(() => vi.unstubAllGlobals());

describe('AdminProductsPage', () => {
  it('renders the product table', async () => {
    routeAdmin(() => jsonResponse({}, 404));
    renderWithProviders(<AdminProductsPage />, { preloadedState: admin });

    expect(await screen.findByText('Keyboard')).toBeInTheDocument();
  });

  it('creates a product via POST and closes the form', async () => {
    const fetchMock = routeAdmin((req) => {
      if (req.method === 'POST') return jsonResponse({ ...product, id: 2, name: 'Mouse' }, 201);
      return jsonResponse({}, 404);
    });
    const user = userEvent.setup();
    renderWithProviders(<AdminProductsPage />, { preloadedState: admin });

    await user.click(await screen.findByRole('button', { name: 'Добавить товар' }));
    await user.type(screen.getByLabelText('Название'), 'Mouse');
    await user.type(screen.getByLabelText('Цена'), '24.99');
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => {
      const posted = fetchMock.mock.calls.some((c) => (c[0] as Request).method === 'POST');
      expect(posted).toBe(true);
    });
    await waitFor(() => expect(screen.queryByText('Новый товар')).not.toBeInTheDocument());
  });

  it('highlights the field from a 400 validation error', async () => {
    routeAdmin((req) => {
      if (req.method === 'POST') {
        return jsonResponse(
          {
            timestamp: 't',
            status: 400,
            error: 'Bad Request',
            message: 'Validation failed',
            path: '/api/products',
            violations: [{ field: 'name', message: 'Название уже занято' }],
          },
          400,
        );
      }
      return jsonResponse({}, 404);
    });
    const user = userEvent.setup();
    renderWithProviders(<AdminProductsPage />, { preloadedState: admin });

    await user.click(await screen.findByRole('button', { name: 'Добавить товар' }));
    await user.type(screen.getByLabelText('Название'), 'Keyboard');
    await user.type(screen.getByLabelText('Цена'), '10.00');
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Название уже занято')).toBeInTheDocument();
    expect(screen.getByText('Новый товар')).toBeInTheDocument(); // form stays open
  });

  it('deletes a product after confirmation', async () => {
    const fetchMock = routeAdmin((req) => {
      if (req.method === 'DELETE') return jsonResponse(null, 204);
      return jsonResponse({}, 404);
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<AdminProductsPage />, { preloadedState: admin });

    await user.click(await screen.findByRole('button', { name: 'Удалить' }));

    await waitFor(() => {
      const deleted = fetchMock.mock.calls.some(
        (c) => (c[0] as Request).method === 'DELETE' && (c[0] as Request).url.endsWith('/products/1'),
      );
      expect(deleted).toBe(true);
    });
  });
});

describe('AdminProductsPage — delete is cancelled', () => {
  beforeEach(() => {
    routeAdmin((req) => {
      if (req.method === 'DELETE') return jsonResponse(null, 204);
      return jsonResponse({}, 404);
    });
  });

  it('does not call DELETE when confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const user = userEvent.setup();
    renderWithProviders(<AdminProductsPage />, { preloadedState: admin });

    await user.click(await screen.findByRole('button', { name: 'Удалить' }));

    const deleteCalled = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.some(
      (c) => (c[0] as Request).method === 'DELETE',
    );
    expect(deleteCalled).toBe(false);
  });
});
