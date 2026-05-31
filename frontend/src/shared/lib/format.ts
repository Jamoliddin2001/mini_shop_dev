// Display formatters. Centralized so money/date rendering stays consistent and the
// Intl instances are created once (not per render). See note in shared/types/api.ts:
// money is only ever formatted here — the frontend never does money arithmetic.

// Single source for the shop currency. The backend stores plain BigDecimal amounts with
// no currency, so this is a frontend display choice — change it in one place if the shop
// uses another currency (ISO 4217 code).
const CURRENCY = 'USD';
const LOCALE = 'ru-RU';

const priceFormatter = new Intl.NumberFormat(LOCALE, {
  style: 'currency',
  currency: CURRENCY,
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const dateFormatter = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

/** Format a numeric amount as currency, e.g. 89.5 -> "89,50 $". */
export function formatPrice(amount: number): string {
  return priceFormatter.format(amount);
}

/** Format an ISO-8601 instant for display; returns the raw string if unparseable. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : dateFormatter.format(date);
}
