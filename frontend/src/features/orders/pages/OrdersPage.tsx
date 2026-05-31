import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useListOrdersQuery } from '@/features/orders/ordersApi';
import OrderStatusBadge from '@/features/orders/components/OrderStatusBadge';
import QueryState from '@/shared/ui/QueryState';
import Pagination from '@/shared/ui/Pagination';
import { formatPrice, formatDate } from '@/shared/lib/format';

const PAGE_SIZE = 10;

/** Paginated order history (summaries without lines). Rows link to the detail page. */
export default function OrdersPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useListOrdersQuery({
    page,
    size: PAGE_SIZE,
  });

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold">Мои заказы</h1>

      <QueryState
        isLoading={isLoading}
        isError={isError}
        error={error}
        isEmpty={data?.content.length === 0}
        emptyMessage="Заказов пока нет."
        onRetry={refetch}
      >
        <div className="card">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-surface-border text-left text-slate-500">
                <th className="pb-2">№</th>
                <th className="pb-2">Дата</th>
                <th className="pb-2">Статус</th>
                <th className="pb-2">Сумма</th>
                <th className="pb-2" />
              </tr>
            </thead>
            <tbody>
              {data?.content.map((order) => (
                <tr key={order.id} className="border-b border-surface-border last:border-0">
                  <td className="py-3 font-medium">#{order.id}</td>
                  <td className="py-3">{formatDate(order.createdAt)}</td>
                  <td className="py-3"><OrderStatusBadge status={order.status} /></td>
                  <td className="py-3 font-medium">{formatPrice(order.totalAmount)}</td>
                  <td className="py-3 text-right">
                    <Link to={`/orders/${order.id}`} className="text-brand-600 hover:underline">
                      Подробнее
                    </Link>
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
