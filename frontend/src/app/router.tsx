import { createBrowserRouter } from 'react-router-dom';
import AppLayout from '@/app/layout/AppLayout';
import PrivateRoute from '@/features/auth/guards/PrivateRoute';
import RoleRoute from '@/features/auth/guards/RoleRoute';
import LoginPage from '@/features/auth/pages/LoginPage';
import RegisterPage from '@/features/auth/pages/RegisterPage';
import CatalogPage from '@/features/products/pages/CatalogPage';
import AdminProductsPage from '@/features/products/pages/AdminProductsPage';
import CartPage from '@/features/cart/pages/CartPage';
import OrdersPage from '@/features/orders/pages/OrdersPage';
import OrderDetailPage from '@/features/orders/pages/OrderDetailPage';
import PlaceholderPage from '@/shared/ui/PlaceholderPage';

/**
 * Application routes. AppLayout is the shared shell; public routes are open, protected
 * routes are wrapped by PrivateRoute, and admin routes by RoleRoute.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <CatalogPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        path: 'cart',
        element: (
          <PrivateRoute>
            <CartPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'orders',
        element: (
          <PrivateRoute>
            <OrdersPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'orders/:id',
        element: (
          <PrivateRoute>
            <OrderDetailPage />
          </PrivateRoute>
        ),
      },
      {
        path: 'admin/products',
        element: (
          <RoleRoute role="ADMIN">
            <AdminProductsPage />
          </RoleRoute>
        ),
      },
      { path: '*', element: <PlaceholderPage title="404" note="Страница не найдена." /> },
    ],
  },
]);
