interface PlaceholderPageProps {
  title: string;
  note?: string;
}

/**
 * Stub page used to prove routing + guards work before the business pages exist
 * (catalog, cart, orders, admin land in the next phase).
 */
export default function PlaceholderPage({ title, note }: PlaceholderPageProps) {
  return (
    <div className="card">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-sm text-slate-500">{note ?? 'Скоро здесь появится содержимое.'}</p>
    </div>
  );
}
