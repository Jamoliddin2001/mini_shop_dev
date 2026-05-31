import { describe, it, expect, afterEach, vi } from 'vitest';
import { makeStore } from '@/app/store';
import { productsApi } from '@/features/products/productsApi';
import { installFetch, jsonResponse } from '@/test/mockFetch';

const emptyPage = { content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 };

afterEach(() => vi.unstubAllGlobals());

describe('productsApi — listProducts query params', () => {
  it('drops empty/undefined filters and applies the default sort', async () => {
    const fetchMock = installFetch(() => jsonResponse(emptyPage, 200));
    const store = makeStore();

    await store.dispatch(
      productsApi.endpoints.listProducts.initiate({
        name: '',
        categoryId: 1,
        minPrice: undefined,
        maxPrice: 50,
        page: 0,
        size: 12,
      }),
    );

    const url = (fetchMock.mock.calls[0][0] as Request).url;
    expect(url).toContain('categoryId=1');
    expect(url).toContain('maxPrice=50');
    expect(url).toContain('sort=createdAt%2Cdesc'); // "createdAt,desc" url-encoded
    expect(url).not.toContain('name=');
    expect(url).not.toContain('minPrice=');
  });

  it('keeps an explicit sort instead of the default', async () => {
    const fetchMock = installFetch(() => jsonResponse(emptyPage, 200));
    const store = makeStore();

    await store.dispatch(
      productsApi.endpoints.listProducts.initiate({ sort: 'price,asc' }),
    );

    const url = (fetchMock.mock.calls[0][0] as Request).url;
    expect(url).toContain('sort=price%2Casc');
  });
});
