import { useNavigate, Link } from 'react-router-dom';
import { useGetCartQuery, useRemoveFromCartMutation } from '@/features/cart/cartApi';
import { useCreateOrderMutation } from '@/features/orders/ordersApi';
import QueryState from '@/shared/ui/QueryState';
import { formatPrice } from '@/shared/lib/format';
import { parseApiError } from '@/shared/lib/apiError';
import { logger } from '@/shared/lib/logger';

/**
 * Server-side cart: shows the lines, lets the user remove items, and places the order.
 * Checkout posts no body (the order is built from this cart server-side); on success the
 * cart is cleared and we navigate to the order confirmation.
 */
export default function CartPage() {
  const navigate = useNavigate();
  const { data: cart, isLoading, isError, error, refetch } = useGetCartQuery();
  const [removeFromCart, { isLoading: isRemoving }] = useRemoveFromCartMutation();
  const [createOrder, { isLoading: isPlacing, error: checkoutError }] = useCreateOrderMutation();

  const handleCheckout = async () => {
    logger.info('[cart] checkout');
    try {
      const order = await createOrder().unwrap();
      navigate(`/orders/${order.id}`, { state: { justCreated: true } });
    } catch {
      logger.warn('[cart] checkout failed');
    }
  };

  return (
    <div>
      <h1 className="mb-6 text-2xl font-bold">Корзина</h1>

      <QueryState
        isLoading={isLoading}
        isError={isError}
        error={error}
        isEmpty={cart?.items.length === 0}
        emptyMessage="Корзина пуста."
        onRetry={refetch}
      >
        {/* QueryState short-circuits on isEmpty, so here the cart always has items. */}
        <div className="card">
          <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-surface-border text-left text-slate-500">
                  <th className="pb-2">Товар</th>
                  <th className="pb-2">Цена</th>
                  <th className="pb-2">Кол-во</th>
                  <th className="pb-2">Сумма</th>
                  <th className="pb-2" />
                </tr>
              </thead>
              <tbody>
                {cart?.items.map((item) => (
                  <tr key={item.productId} className="border-b border-surface-border last:border-0">
                    <td className="py-3">{item.productName}</td>
                    <td className="py-3">{formatPrice(item.unitPrice)}</td>
                    <td className="py-3">{item.quantity}</td>
                    <td className="py-3 font-medium">{formatPrice(item.lineTotal)}</td>
                    <td className="py-3 text-right">
                      <button
                        type="button"
                        className="btn-danger"
                        onClick={() => removeFromCart(item.productId)}
                        disabled={isRemoving}
                      >
                        Удалить
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="mt-6 flex items-center justify-between">
              <p className="text-sm text-slate-500">
                Товаров: {cart?.totalItems}
              </p>
              <p className="text-lg font-semibold">
                Итого: {cart ? formatPrice(cart.totalAmount) : ''}
              </p>
            </div>

            {checkoutError && (
              <p role="alert" className="mt-3 text-sm text-danger-600">
                {parseApiError(checkoutError).message}
              </p>
            )}

            <button
              type="button"
              className="btn-primary mt-4 w-full"
              onClick={handleCheckout}
              disabled={isPlacing || !cart || cart.items.length === 0}
            >
              {isPlacing ? 'Оформляем…' : 'Оформить заказ'}
            </button>
        </div>
      </QueryState>

      <p className="mt-4 text-sm">
        <Link to="/" className="text-brand-600 hover:underline">← Вернуться в каталог</Link>
      </p>
    </div>
  );
}
