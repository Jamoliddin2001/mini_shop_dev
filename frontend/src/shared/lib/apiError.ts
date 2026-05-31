import type { ApiError } from '@/shared/types/api';

export interface ParsedApiError {
  /** Human-readable top-level message to show above the form. */
  message: string;
  /** Field name -> first validation message, from ApiError.violations. */
  fields: Record<string, string>;
}

/** Type guard: does this value look like our backend ApiError envelope? */
function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'message' in value &&
    typeof (value as { message: unknown }).message === 'string'
  );
}

/**
 * Normalizes an RTK Query error (`FetchBaseQueryError | SerializedError | unknown`) into
 * a shape the auth forms can render. The backend always answers with the ApiError
 * envelope, carried in `error.data`; we fall back to a generic message otherwise.
 */
export function parseApiError(error: unknown): ParsedApiError {
  // RTK Query FetchBaseQueryError shape: { status, data }
  const data = (error as { data?: unknown } | undefined)?.data;

  if (isApiError(data)) {
    const fields: Record<string, string> = {};
    for (const v of data.violations ?? []) {
      if (!(v.field in fields)) fields[v.field] = v.message;
    }
    return { message: data.message, fields };
  }

  // Network / unexpected errors (no JSON body).
  const status = (error as { status?: unknown } | undefined)?.status;
  if (status === 'FETCH_ERROR') {
    return { message: 'Сервер недоступен. Проверьте подключение.', fields: {} };
  }

  return { message: 'Произошла ошибка. Попробуйте ещё раз.', fields: {} };
}
