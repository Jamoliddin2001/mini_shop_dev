import { useEffect, useRef } from 'react';
import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { selectToken, selectUser, setUser } from '@/features/auth/authSlice';
import { useLazyGetMeQuery } from '@/features/auth/authApi';
import { logger } from '@/shared/lib/logger';

/**
 * On startup, if a token was restored from storage but the principal is not yet known,
 * fetch GET /api/me to hydrate it. A stale/expired token yields 401, which
 * baseQueryWithReauth turns into a logout — so this also self-heals dead sessions.
 *
 * Mounted once (in AppLayout); the ref guards against duplicate fetches under StrictMode.
 */
export function useAuthBootstrap(): void {
  const dispatch = useAppDispatch();
  const token = useAppSelector(selectToken);
  const user = useAppSelector(selectUser);
  const [fetchMe] = useLazyGetMeQuery();
  const requested = useRef(false);

  useEffect(() => {
    if (!token || user || requested.current) return;
    requested.current = true;
    logger.debug('[bootstrap] hydrating user from token');
    fetchMe()
      .unwrap()
      .then((me) => dispatch(setUser(me)))
      .catch(() => {
        // 401 already cleared the session via baseQueryWithReauth; nothing else to do.
      });
  }, [token, user, fetchMe, dispatch]);
}
