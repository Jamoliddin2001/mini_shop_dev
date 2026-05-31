import { baseApi } from '@/app/api/baseApi';
import { logger } from '@/shared/lib/logger';
import type { AddToCartRequest, Cart } from '@/shared/types/api';

/**
 * Server-side cart endpoints (one cart per user, keyed by the JWT — never by a body/path
 * id). All require auth; anonymous calls get 401 and the baseQuery clears the session.
 * Both mutations return the updated Cart, and invalidate the 'Cart' tag so getCart
 * refetches — keeping a single source of truth instead of merging responses by hand.
 */
export const cartApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getCart: builder.query<Cart, void>({
      query: () => ({ url: '/cart' }),
      providesTags: ['Cart'],
    }),

    // POST sums quantity into the existing line (backend invariant UNIQUE(cart,product)).
    addToCart: builder.mutation<Cart, AddToCartRequest>({
      query: (body) => {
        logger.debug('[cart] add', { productId: body.productId, quantity: body.quantity });
        return { url: '/cart/items', method: 'POST', body };
      },
      invalidatesTags: ['Cart'],
    }),

    removeFromCart: builder.mutation<Cart, number>({
      query: (productId) => {
        logger.debug('[cart] remove', { productId });
        return { url: `/cart/items/${productId}`, method: 'DELETE' };
      },
      invalidatesTags: ['Cart'],
    }),
  }),
});

export const { useGetCartQuery, useAddToCartMutation, useRemoveFromCartMutation } = cartApi;
