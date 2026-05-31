import type { ReactNode } from 'react';
import { parseApiError } from '@/shared/lib/apiError';

interface QueryStateProps {
  /** RTK Query `isLoading` (first load). */
  isLoading: boolean;
  /** RTK Query `isError`. */
  isError: boolean;
  /** The RTK Query `error` value, rendered via parseApiError. */
  error?: unknown;
  /** True when the request succeeded but returned no items. */
  isEmpty?: boolean;
  /** Message shown for the empty state. */
  emptyMessage?: string;
  /** Optional retry handler (RTK Query `refetch`). */
  onRetry?: () => void;
  children: ReactNode;
}

/**
 * Single source of truth for the loading / error / empty branches of a data screen.
 * Every list/detail page funnels its RTK Query result through this so the three states
 * are handled identically and the branching isn't duplicated across features (KISS,
 * and the explicit "loading/error/empty handled" requirement from the spec).
 */
export default function QueryState({
  isLoading,
  isError,
  error,
  isEmpty = false,
  emptyMessage = 'Ничего не найдено.',
  onRetry,
  children,
}: QueryStateProps) {
  if (isLoading) {
    return (
      <div className="flex justify-center py-12" role="status" aria-label="Загрузка">
        <span className="spinner" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="card text-center" role="alert">
        <p className="text-sm text-danger-600">{parseApiError(error).message}</p>
        {onRetry && (
          <button type="button" className="btn-ghost mt-3" onClick={onRetry}>
            Повторить
          </button>
        )}
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="card text-center text-sm text-slate-500">{emptyMessage}</div>
    );
  }

  return <>{children}</>;
}
