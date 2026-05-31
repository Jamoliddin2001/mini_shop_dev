import { baseApi } from '@/app/api/baseApi';
import { logger } from '@/shared/lib/logger';
import type { Order, OrderSummary, PageResponse } from '@/shared/types/api';

/**
 * Order endpoints. Checkout (createOrder) takes NO body: the order is built from the
 * server-side cart, and on success the cart is cleared server-side. We therefore
 * invalidate both 'Cart' and 'Orders' so the cart empties and the history refreshes
 * without manual cache surgery. An empty cart yields 400 "Cart is empty".
 */
export const ordersApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    createOrder: builder.mutation<Order, void>({
      query: () => {
        logger.info('[orders] checkout requested');
        return { url: '/orders', method: 'POST' };
      },
      invalidatesTags: ['Cart', 'Orders'],
    }),

    listOrders: builder.query<PageResponse<OrderSummary>, { page?: number; size?: number }>({
      query: ({ page = 0, size = 10 } = {}) => ({
        url: '/orders',
        params: { page, size },
      }),
      providesTags: ['Orders'],
    }),

    // A foreign order returns 404 (not 403) — the backend does not reveal others' resources.
    getOrder: builder.query<Order, number>({
      query: (id) => ({ url: `/orders/${id}` }),
      providesTags: ['Orders'],
    }),
  }),
});

export const { useCreateOrderMutation, useListOrdersQuery, useGetOrderQuery } = ordersApi;
