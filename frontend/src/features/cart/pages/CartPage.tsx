import { useNavigate, Link } from 'react-router-dom';
import { useGetCartQuery, useAddToCartMutation, useRemoveFromCartMutation } from '@/features/cart/cartApi';
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
  const [addToCart, { isLoading: isAdding }] = useAddToCartMutation();
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
        <div className={`card${isPlacing ? ' opacity-60 pointer-events-none' : ''}`}>
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
                    <td className="py-3">
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          className="btn-ghost px-2 py-0 text-lg leading-none"
                          onClick={() => removeFromCart(item.productId)}
                          disabled={isRemoving || isAdding || item.quantity !== 1}
                          title={item.quantity > 1 ? 'Уменьшение на 1 не поддерживается — удалите товар' : 'Убрать товар'}
                        >
                          −
                        </button>
                        <span className="w-6 text-center">{item.quantity}</span>
                        <button
                          type="button"
                          className="btn-ghost px-2 py-0 text-lg leading-none"
                          onClick={() => addToCart({ productId: item.productId, quantity: 1 })}
                          disabled={isAdding || isRemoving}
                        >
                          +
                        </button>
                      </div>
                    </td>
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
              {isPlacing ? (
                <span className="flex items-center justify-center gap-2">
                  <svg
                    className="h-4 w-4 animate-spin"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  Оформляем…
                </span>
              ) : 'Оформить заказ'}
            </button>
        </div>
      </QueryState>

      <p className="mt-4 text-sm">
        <Link to="/" className="text-brand-600 hover:underline">← Вернуться в каталог</Link>
      </p>
    </div>
  );
}
