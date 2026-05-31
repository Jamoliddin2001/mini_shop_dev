import { describe, it, expect, afterEach, vi } from 'vitest';
import { makeStore } from '@/app/store';
import { ordersApi } from '@/features/orders/ordersApi';
import { installFetch, jsonResponse } from '@/test/mockFetch';

const order = {
  id: 7,
  status: 'NEW' as const,
  totalAmount: 50,
  createdAt: '2026-05-30T10:00:00Z',
  items: [],
};

afterEach(() => vi.unstubAllGlobals());

describe('ordersApi', () => {
  it('checkout POSTs to /orders with no body', async () => {
    const fetchMock = installFetch(() => jsonResponse(order, 201));
    const store = makeStore();

    await store.dispatch(ordersApi.endpoints.createOrder.initiate());

    const req = fetchMock.mock.calls[0][0] as Request;
    expect(req.method).toBe('POST');
    expect(req.url).toMatch(/\/orders$/);
  });

  it('listOrders carries the page and size params', async () => {
    const fetchMock = installFetch(() =>
      jsonResponse({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 }),
    );
    const store = makeStore();

    await store.dispatch(ordersApi.endpoints.listOrders.initiate({ page: 1, size: 10 }));

    const url = (fetchMock.mock.calls[0][0] as Request).url;
    expect(url).toContain('page=1');
    expect(url).toContain('size=10');
  });
});
