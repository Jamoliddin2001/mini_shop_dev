import { baseApi } from '@/app/api/baseApi';
import { logger } from '@/shared/lib/logger';
import type {
  Category,
  PageResponse,
  Product,
  ProductFilter,
  ProductWriteRequest,
} from '@/shared/types/api';

/**
 * Drops undefined / empty-string / NaN params so the request URL only carries real
 * filters (the backend returns 400 for malformed values, so we never send blanks).
 */
function cleanFilter(filter: ProductFilter): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(filter)) {
    if (value === undefined || value === '' || (typeof value === 'number' && Number.isNaN(value))) {
      continue;
    }
    params[key] = value;
  }
  // Default sort matches the backend default; kept explicit so the UI is deterministic.
  if (params.sort === undefined) params.sort = 'createdAt,desc';
  return params;
}

/**
 * Product + category endpoints injected into the shared baseApi. Catalog reads are
 * public; create/update/delete require an ADMIN token (enforced by the backend — the
 * UI guard is convenience only). Mutations invalidate the 'Products' tag so any visible
 * list refetches automatically.
 */
export const productsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    listProducts: builder.query<PageResponse<Product>, ProductFilter>({
      query: (filter) => {
        const params = cleanFilter(filter);
        logger.debug('[products] list', { params });
        return { url: '/products', params };
      },
      providesTags: ['Products'],
    }),

    getProduct: builder.query<Product, number>({
      query: (id) => ({ url: `/products/${id}` }),
      providesTags: ['Products'],
    }),

    createProduct: builder.mutation<Product, ProductWriteRequest>({
      query: (body) => {
        logger.info('[products] create', { name: body.name });
        return { url: '/products', method: 'POST', body };
      },
      invalidatesTags: ['Products'],
    }),

    updateProduct: builder.mutation<Product, { id: number; body: ProductWriteRequest }>({
      query: ({ id, body }) => {
        logger.info('[products] update', { id });
        return { url: `/products/${id}`, method: 'PUT', body };
      },
      invalidatesTags: ['Products'],
    }),

    deleteProduct: builder.mutation<void, number>({
      query: (id) => {
        logger.info('[products] delete', { id });
        return { url: `/products/${id}`, method: 'DELETE' };
      },
      invalidatesTags: ['Products'],
    }),

    listCategories: builder.query<Category[], void>({
      query: () => ({ url: '/categories' }),
    }),
  }),
});

export const {
  useListProductsQuery,
  useGetProductQuery,
  useCreateProductMutation,
  useUpdateProductMutation,
  useDeleteProductMutation,
  useListCategoriesQuery,
} = productsApi;
