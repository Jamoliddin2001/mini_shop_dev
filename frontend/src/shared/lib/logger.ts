/**
 * Thin, env-gated logging facade over `console`.
 *
 * - `debug` / `info` are active only in dev builds (`import.meta.env.DEV`); in a
 *   production bundle they are no-ops, so verbose development logging never ships.
 * - `warn` / `error` are always active.
 *
 * Security: NEVER pass a raw token, password, or other secret. Use {@link mask} to
 * redact such values before logging — mirrors the backend rule "не логируем токены/PII".
 */
const isDev = import.meta.env.DEV;

type LogArgs = unknown[];

/** Redacts a secret for safe logging: keeps presence/length signal, hides the value. */
export function mask(value: string | null | undefined): string {
  if (!value) return '<empty>';
  return `***(${value.length} chars)`;
}

export const logger = {
  debug(...args: LogArgs): void {
    if (isDev) console.debug('[debug]', ...args);
  },
  info(...args: LogArgs): void {
    if (isDev) console.info('[info]', ...args);
  },
  warn(...args: LogArgs): void {
    console.warn('[warn]', ...args);
  },
  error(...args: LogArgs): void {
    console.error('[error]', ...args);
  },
};
