import { Link, useLocation, useParams } from 'react-router-dom';
import { useGetOrderQuery } from '@/features/orders/ordersApi';
import OrderStatusBadge from '@/features/orders/components/OrderStatusBadge';
import QueryState from '@/shared/ui/QueryState';
import { formatPrice, formatDate } from '@/shared/lib/format';
import { logger } from '@/shared/lib/logger';

interface OrderDetailLocationState {
  justCreated?: boolean;
}

/**
 * Order detail + checkout confirmation. The backend's POST /orders takes no body (the
 * order is assembled from the server cart), so there is no address/payment form — this
 * screen IS the "checkout form": a confirmation/review of the placed order. When reached
 * straight after checkout (`location.state.justCreated`) we show a success banner.
 */
export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const justCreated = (location.state as OrderDetailLocationState | null)?.justCreated ?? false;

  const orderId = Number(id);
  const isValidId = id !== undefined && id.trim() !== '' && !Number.isNaN(orderId);
  logger.debug('[orders] view', { orderId });

  const {
    data: order,
    isLoading,
    isError,
    error,
    refetch,
  } = useGetOrderQuery(orderId, {
    skip: !isValidId,
  });

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Заказ #{id}</h1>
        <Link to="/orders" className="text-sm text-brand-600 hover:underline">
          ← К списку заказов
        </Link>
      </div>

      {!isValidId ? (
        <div className="card text-sm text-slate-500">Некорректный номер заказа.</div>
      ) : (
        <>
          {justCreated && (
            <div className="card mb-6 border-brand-500 bg-brand-50 text-brand-700" role="status">
              Заказ успешно оформлен. Корзина очищена.
            </div>
          )}

          <QueryState isLoading={isLoading} isError={isError} error={error} onRetry={refetch}>
            {order && (
              <div className="card">
                <div className="mb-4 flex items-center justify-between">
                  <OrderStatusBadge status={order.status} />
                  <span className="text-sm text-slate-500">{formatDate(order.createdAt)}</span>
                </div>

                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-surface-border text-left text-slate-500">
                      <th className="pb-2">Товар</th>
                      <th className="pb-2">Цена</th>
                      <th className="pb-2">Кол-во</th>
                      <th className="pb-2">Сумма</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.items.map((item) => (
                      <tr
                        key={item.productId}
                        className="border-b border-surface-border last:border-0"
                      >
                        <td className="py-3">{item.productName}</td>
                        <td className="py-3">{formatPrice(item.unitPrice)}</td>
                        <td className="py-3">{item.quantity}</td>
                        <td className="py-3 font-medium">{formatPrice(item.lineTotal)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <p className="mt-6 text-right text-lg font-semibold">
                  Итого: {formatPrice(order.totalAmount)}
                </p>
              </div>
            )}
          </QueryState>
        </>
      )}
    </div>
  );
}
