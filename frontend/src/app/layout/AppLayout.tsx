import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { logout, selectIsAuthenticated, selectUser } from '@/features/auth/authSlice';
import { useAuthBootstrap } from '@/features/auth/useAuthBootstrap';

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `text-sm font-medium transition-colors ${
    isActive ? 'text-brand-600' : 'text-slate-600 hover:text-brand-600'
  }`;

/**
 * Root layout: a header that reflects the auth state plus an <Outlet/> for routed pages.
 * Presentational — auth actions are delegated to the store.
 */
export default function AppLayout() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const user = useAppSelector(selectUser);

  useAuthBootstrap();

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  return (
    <div className="min-h-screen">
      <header className="border-b border-surface-border bg-surface">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-brand-600">
            Mini Shop
          </Link>

          <nav className="flex items-center gap-6">
            <NavLink to="/" className={navLinkClass} end>
              Каталог
            </NavLink>
            {isAuthenticated && (
              <>
                <NavLink to="/cart" className={navLinkClass}>
                  Корзина
                </NavLink>
                <NavLink to="/orders" className={navLinkClass}>
                  Заказы
                </NavLink>
              </>
            )}
            {user?.role === 'ADMIN' && (
              <NavLink to="/admin/products" className={navLinkClass}>
                Админка
              </NavLink>
            )}
          </nav>

          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <>
                <span className="text-sm text-slate-500">
                  {user?.email}
                  {user?.role && (
                    <span className="ml-1 rounded bg-surface-muted px-1.5 py-0.5 text-xs uppercase text-slate-400">
                      {user.role}
                    </span>
                  )}
                </span>
                <button type="button" className="btn-ghost" onClick={handleLogout}>
                  Выйти
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="btn-ghost">
                  Войти
                </Link>
                <Link to="/register" className="btn-primary">
                  Регистрация
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
