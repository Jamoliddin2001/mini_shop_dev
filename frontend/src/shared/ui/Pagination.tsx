interface PaginationProps {
  /** Zero-based current page (PageResponse.page). */
  page: number;
  /** Total number of pages (PageResponse.totalPages). */
  totalPages: number;
  /** Called with the new zero-based page index. */
  onPageChange: (page: number) => void;
}

/**
 * Minimal prev/next pager driven by the PageResponse envelope. Hidden when there is at
 * most one page so empty/single-page screens stay clean.
 */
export default function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <nav className="mt-6 flex items-center justify-center gap-4" aria-label="Пагинация">
      <button
        type="button"
        className="btn-ghost"
        onClick={() => onPageChange(page - 1)}
        disabled={isFirst}
      >
        ← Назад
      </button>
      <span className="text-sm text-slate-500">
        Стр. {page + 1} из {totalPages}
      </span>
      <button
        type="button"
        className="btn-ghost"
        onClick={() => onPageChange(page + 1)}
        disabled={isLast}
      >
        Вперёд →
      </button>
    </nav>
  );
}
