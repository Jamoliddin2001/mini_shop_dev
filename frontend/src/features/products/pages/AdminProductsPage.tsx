import { useState } from 'react';
import {
  useCreateProductMutation,
  useDeleteProductMutation,
  useListCategoriesQuery,
  useListProductsQuery,
  useUpdateProductMutation,
} from '@/features/products/productsApi';
import ProductForm from '@/features/products/components/ProductForm';
import QueryState from '@/shared/ui/QueryState';
import Pagination from '@/shared/ui/Pagination';
import { formatPrice } from '@/shared/lib/format';
import { logger } from '@/shared/lib/logger';
import type { Product, ProductWriteRequest } from '@/shared/types/api';

const PAGE_SIZE = 10;

type Editing = { mode: 'create' } | { mode: 'edit'; product: Product } | null;

/**
 * Admin product management: list + create/edit/delete. Defense in depth — the route is
 * behind RoleRoute role="ADMIN" and the nav link is hidden for non-admins (Phase 7), but
 * the BACKEND is the real authority (401/403). UI hiding is convenience, not security.
 */
export default function AdminProductsPage() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Editing>(null);

  const { data, isLoading, isError, error, refetch } = useListProductsQuery({
    page,
    size: PAGE_SIZE,
  });
  const { data: categories } = useListCategoriesQuery();
  const [createProduct, { isLoading: isCreating, error: createError, reset: resetCreate }] =
    useCreateProductMutation();
  const [updateProduct, { isLoading: isUpdating, error: updateError, reset: resetUpdate }] =
    useUpdateProductMutation();
  const [deleteProduct] = useDeleteProductMutation();

  const closeForm = () => {
    setEditing(null);
    resetCreate();
    resetUpdate();
  };

  const openCreate = () => {
    resetCreate();
    resetUpdate();
    setEditing({ mode: 'create' });
  };

  const openEdit = (product: Product) => {
    resetCreate();
    resetUpdate();
    setEditing({ mode: 'edit', product });
  };

  const handleSubmit = async (body: ProductWriteRequest) => {
    try {
      if (editing?.mode === 'edit') {
        await updateProduct({ id: editing.product.id, body }).unwrap();
      } else {
        await createProduct(body).unwrap();
      }
      closeForm();
    } catch {
      // Validation/server errors stay rendered from the mutation's `error` state;
      // keep the form open so the user can correct and resubmit.
      logger.warn('[admin] product save failed');
    }
  };

  const handleDelete = async (product: Product) => {
    if (!window.confirm(`Удалить товар «${product.name}»?`)) return;
    logger.info('[admin] product delete confirmed', { id: product.id });
    try {
      await deleteProduct(product.id).unwrap();
    } catch {
      logger.warn('[admin] product delete failed', { id: product.id });
    }
  };

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Управление товарами</h1>
        {!editing && (
          <button type="button" className="btn-primary" onClick={openCreate}>
            Добавить товар
          </button>
        )}
      </div>

      {editing && (
        <ProductForm
          initial={editing.mode === 'edit' ? editing.product : null}
          categories={categories ?? []}
          onSubmit={handleSubmit}
          onCancel={closeForm}
          isSaving={isCreating || isUpdating}
          saveError={editing.mode === 'edit' ? updateError : createError}
        />
      )}

      <QueryState
        isLoading={isLoading}
        isError={isError}
        error={error}
        isEmpty={data?.content.length === 0}
        emptyMessage="Товаров пока нет."
        onRetry={refetch}
      >
        <div className="card">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-surface-border text-left text-slate-500">
                <th className="pb-2">Название</th>
                <th className="pb-2">Категория</th>
                <th className="pb-2">Цена</th>
                <th className="pb-2" />
              </tr>
            </thead>
            <tbody>
              {data?.content.map((product) => (
                <tr key={product.id} className="border-b border-surface-border last:border-0">
                  <td className="py-3">{product.name}</td>
                  <td className="py-3 text-slate-500">{product.categoryName ?? '—'}</td>
                  <td className="py-3 font-medium">{formatPrice(product.price)}</td>
                  <td className="py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button type="button" className="btn-ghost" onClick={() => openEdit(product)}>
                        Изменить
                      </button>
                      <button
                        type="button"
                        className="btn-danger"
                        onClick={() => handleDelete(product)}
                      >
                        Удалить
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && (
          <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        )}
      </QueryState>
    </div>
  );
}
