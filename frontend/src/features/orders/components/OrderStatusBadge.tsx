import type { OrderStatus } from '@/shared/types/api';

// Status -> badge styling + Russian label. Shared by the list and detail pages so the
// mapping lives in one place.
const STATUS: Record<OrderStatus, { label: string; className: string }> = {
  NEW: { label: 'Новый', className: 'bg-brand-100 text-brand-700' },
  PAID: { label: 'Оплачен', className: 'bg-brand-500 text-white' },
  CANCELLED: { label: 'Отменён', className: 'bg-danger-50 text-danger-600' },
};

export default function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { label, className } = STATUS[status];
  return <span className={`badge ${className}`}>{label}</span>;
}
