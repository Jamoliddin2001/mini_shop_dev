import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from '@/app/hooks';
import { selectIsAuthenticated, selectRole } from '@/features/auth/authSlice';
import { logger } from '@/shared/lib/logger';
import type { Role } from '@/shared/types/api';

/**
 * Gate for role-restricted routes (e.g. admin). Anonymous → /login; authenticated but
 * wrong role → home. We redirect rather than show a 403 so we do not advertise the
 * existence of admin resources — mirrors the backend "чужое не раскрываем" stance.
 */
export default function RoleRoute({ role, children }: { role: Role; children: ReactElement }) {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const currentRole = useAppSelector(selectRole);
  const location = useLocation();

  if (!isAuthenticated) {
    logger.debug('[guard] denied (unauthenticated)', { path: location.pathname });
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (currentRole !== role) {
    logger.debug('[guard] denied (role mismatch)', { path: location.pathname, role: currentRole });
    return <Navigate to="/" replace />;
  }

  return children;
}
