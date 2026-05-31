import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from '@/app/hooks';
import { selectIsAuthenticated } from '@/features/auth/authSlice';
import { logger } from '@/shared/lib/logger';

/**
 * Gate for routes that require any authenticated user. Anonymous visitors are sent to
 * /login, remembering where they came from so login can return them there. Runtime 401s
 * clear the token (baseQueryWithReauth) and the next render lands here → redirect.
 */
export default function PrivateRoute({ children }: { children: ReactElement }) {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    logger.debug('[guard] denied (unauthenticated)', { path: location.pathname });
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  return children;
}
