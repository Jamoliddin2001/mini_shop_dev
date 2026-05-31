import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFetch, jsonResponse } from '@/test/mockFetch';
import CatalogPage from '@/features/products/pages/CatalogPage';

const product = {
  id: 1,
  name: 'Mechanical Keyboard',
  description: 'RGB',
  price: 89.5,
  imageUrl: null,
  categoryId: 1,
  categoryName: 'Electronics',
  createdAt: '2026-05-01T10:00:00Z',
};
const page = { content: [product], page: 0, size: 12, totalElements: 1, totalPages: 1 };
const categories = [{ id: 1, name: 'Electronics' }];

function routeCatalog(productsResponse: () => Response) {
  return installFetch((req) => {
    const path = new URL(req.url).pathname;
    if (path.endsWith('/categories')) return jsonResponse(categories);
    if (path.endsWith('/products')) return productsResponse();
    return jsonResponse({ message: 'unexpected' }, 404);
  });
}

const authed = {
  auth: { token: 'jwt', user: { email: 'u@shop.local', role: 'USER' as const } },
};

afterEach(() => vi.unstubAllGlobals());

describe('CatalogPage', () => {
  it('renders products with formatted price (anonymous sees a login prompt)', async () => {
    routeCatalog(() => jsonResponse(page));
    renderWithProviders(<CatalogPage />);

    expect(await screen.findByText('Mechanical Keyboard')).toBeInTheDocument();
    expect(screen.getByText('Войдите, чтобы купить')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'В корзину' })).not.toBeInTheDocument();
  });

  it('shows an add-to-cart button for an authenticated user', async () => {
    routeCatalog(() => jsonResponse(page));
    renderWithProviders(<CatalogPage />, { preloadedState: authed });

    expect(await screen.findByRole('button', { name: 'В корзину' })).toBeInTheDocument();
  });

  it('renders the empty state when no products match', async () => {
    routeCatalog(() => jsonResponse({ ...page, content: [], totalElements: 0, totalPages: 0 }));
    renderWithProviders(<CatalogPage />);

    expect(await screen.findByText(/Ничего не найдено/)).toBeInTheDocument();
  });

  it('renders the error state when the request fails', async () => {
    routeCatalog(() =>
      jsonResponse(
        { timestamp: 't', status: 500, error: 'Internal', message: 'Сбой сервера', path: '/api/products' },
        500,
      ),
    );
    renderWithProviders(<CatalogPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Сбой сервера');
  });

  it('issues a filtered request after typing in the search box (debounced)', async () => {
    const fetchMock = routeCatalog(() => jsonResponse(page));
    const user = userEvent.setup();
    renderWithProviders(<CatalogPage />);

    await screen.findByText('Mechanical Keyboard');
    await user.type(screen.getByLabelText('Название'), 'key');

    await waitFor(() => {
      const productCalls = fetchMock.mock.calls
        .map((c) => (c[0] as Request).url)
        .filter((url) => url.includes('/products'));
      expect(productCalls.some((url) => url.includes('name=key'))).toBe(true);
    });
  });
});
