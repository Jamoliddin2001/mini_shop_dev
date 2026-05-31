import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAppSelector } from '@/app/hooks';
import { selectIsAuthenticated } from '@/features/auth/authSlice';
import { useAddToCartMutation } from '@/features/cart/cartApi';
import { useListCategoriesQuery, useListProductsQuery } from '@/features/products/productsApi';
import QueryState from '@/shared/ui/QueryState';
import Pagination from '@/shared/ui/Pagination';
import { formatPrice } from '@/shared/lib/format';
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue';
import { logger } from '@/shared/lib/logger';
import type { Product, ProductFilter } from '@/shared/types/api';

const PAGE_SIZE = 12; // grid-friendly; backend default 20, hard cap 100.

/** Parses a price input into a filter value: blank -> undefined, else a number. */
function toPrice(value: string): number | undefined {
  if (value.trim() === '') return undefined;
  const n = Number(value);
  return Number.isNaN(n) ? undefined : n;
}

/**
 * Public catalog: server-side filtering (name ILIKE, category, price range) + pagination.
 * Changing any filter resets to page 0. Add-to-cart is shown only to authenticated users;
 * the backend enforces auth regardless.
 */
export default function CatalogPage() {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);

  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [page, setPage] = useState(0);

  // Debounce only the free-text search so typing doesn't fire a request per keystroke.
  const debouncedName = useDebouncedValue(name, 300);

  const filter: ProductFilter = useMemo(
    () => ({
      name: debouncedName.trim() || undefined,
      categoryId,
      minPrice: toPrice(minPrice),
      maxPrice: toPrice(maxPrice),
      page,
      size: PAGE_SIZE,
    }),
    [debouncedName, categoryId, minPrice, maxPrice, page],
  );

  const { data, isLoading, isFetching, isError, error, refetch } = useListProductsQuery(filter);
  const { data: categories } = useListCategoriesQuery();

  // Resets the page whenever a filter (not the page itself) changes.
  const onFilterChange = (apply: () => void) => {
    setPage(0);
    apply();
  };

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold">Каталог товаров</h1>

      <form
        className="card mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
        onSubmit={(e) => e.preventDefault()}
      >
        <div>
          <label htmlFor="f-name" className="mb-1 block text-sm font-medium">Название</label>
          <input
            id="f-name"
            className="input"
            value={name}
            onChange={(e) => onFilterChange(() => setName(e.target.value))}
            placeholder="Поиск по названию"
          />
        </div>
        <div>
          <label htmlFor="f-category" className="mb-1 block text-sm font-medium">Категория</label>
          <select
            id="f-category"
            className="input"
            value={categoryId ?? ''}
            onChange={(e) =>
              onFilterChange(() =>
                setCategoryId(e.target.value ? Number(e.target.value) : undefined),
              )
            }
          >
            <option value="">Все категории</option>
            {categories?.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="f-min" className="mb-1 block text-sm font-medium">Цена от</label>
          <input
            id="f-min"
            type="number"
            min="0"
            className="input"
            value={minPrice}
            onChange={(e) => onFilterChange(() => setMinPrice(e.target.value))}
          />
        </div>
        <div>
          <label htmlFor="f-max" className="mb-1 block text-sm font-medium">Цена до</label>
          <input
            id="f-max"
            type="number"
            min="0"
            className="input"
            value={maxPrice}
            onChange={(e) => onFilterChange(() => setMaxPrice(e.target.value))}
          />
        </div>
      </form>

      <QueryState
        isLoading={isLoading}
        isError={isError}
        error={error}
        isEmpty={data?.content.length === 0}
        emptyMessage="Ничего не найдено. Измените фильтры."
        onRetry={refetch}
      >
        <div
          className={`grid gap-4 sm:grid-cols-2 lg:grid-cols-3 ${isFetching ? 'opacity-60' : ''}`}
        >
          {data?.content.map((product) => (
            <ProductCard key={product.id} product={product} canBuy={isAuthenticated} />
          ))}
        </div>
        {data && (
          <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        )}
      </QueryState>
    </div>
  );
}

function ProductCard({ product, canBuy }: { product: Product; canBuy: boolean }) {
  const [addToCart, { isLoading }] = useAddToCartMutation();

  const handleAdd = async () => {
    logger.debug('[catalog] add to cart', { productId: product.id });
    try {
      await addToCart({ productId: product.id, quantity: 1 }).unwrap();
    } catch {
      logger.warn('[catalog] add to cart failed', { productId: product.id });
    }
  };

  return (
    <div className="card flex flex-col">
      <div className="mb-3 aspect-video overflow-hidden rounded-md bg-surface-muted">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-slate-400">
            нет изображения
          </div>
        )}
      </div>
      <h2 className="font-medium">{product.name}</h2>
      {product.categoryName && <p className="text-xs text-slate-400">{product.categoryName}</p>}
      <p className="mt-auto pt-3 text-lg font-semibold">{formatPrice(product.price)}</p>

      {canBuy ? (
        <button type="button" className="btn-primary mt-3" onClick={handleAdd} disabled={isLoading}>
          {isLoading ? 'Добавляем…' : 'В корзину'}
        </button>
      ) : (
        <Link to="/login" className="btn-ghost mt-3">
          Войдите, чтобы купить
        </Link>
      )}
    </div>
  );
}
