import { useState, type FormEvent } from 'react';
import { parseApiError } from '@/shared/lib/apiError';
import type { Category, Product, ProductWriteRequest } from '@/shared/types/api';

interface ProductFormProps {
  /** Existing product when editing; null/undefined when creating. */
  initial?: Product | null;
  categories: Category[];
  /** Parent runs the create/update mutation; rejects are surfaced via `saveError`. */
  onSubmit: (body: ProductWriteRequest) => Promise<void>;
  onCancel: () => void;
  isSaving: boolean;
  /** The mutation error, used to highlight fields from ApiError.violations. */
  saveError?: unknown;
}

/** Price must fit NUMERIC(12,2): up to 10 integer digits and 2 fraction digits, >= 0. */
const PRICE_RE = /^\d{1,10}(\.\d{1,2})?$/;

/**
 * Create/edit product form, rendered as an inline expanding panel (no modal — KISS).
 * Client validation MIRRORS the backend rules for fast feedback only; the backend stays
 * the source of truth, and its 400 `violations` are mapped back onto the fields.
 */
export default function ProductForm({
  initial,
  categories,
  onSubmit,
  onCancel,
  isSaving,
  saveError,
}: ProductFormProps) {
  const [name, setName] = useState(initial?.name ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [price, setPrice] = useState(initial ? String(initial.price) : '');
  const [imageUrl, setImageUrl] = useState(initial?.imageUrl ?? '');
  const [categoryId, setCategoryId] = useState<string>(
    initial?.categoryId ? String(initial.categoryId) : '',
  );
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({});

  const serverFields = saveError ? parseApiError(saveError).fields : {};
  const serverMessage = saveError ? parseApiError(saveError).message : null;
  const errorFor = (field: string) => clientErrors[field] ?? serverFields[field];

  const validate = (): Record<string, string> => {
    const errors: Record<string, string> = {};
    if (!name.trim()) errors.name = 'Название обязательно.';
    else if (name.length > 255) errors.name = 'Не длиннее 255 символов.';
    if (description.length > 4000) errors.description = 'Не длиннее 4000 символов.';
    if (price.trim() === '') errors.price = 'Цена обязательна.';
    else if (!PRICE_RE.test(price.trim())) errors.price = 'Некорректная цена (до 10 цифр и 2 знаков).';
    if (imageUrl.length > 1024) errors.imageUrl = 'Ссылка не длиннее 1024 символов.';
    return errors;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const errors = validate();
    setClientErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const body: ProductWriteRequest = {
      name: name.trim(),
      price: Number(price),
      ...(description.trim() ? { description: description.trim() } : {}),
      ...(imageUrl.trim() ? { imageUrl: imageUrl.trim() } : {}),
      ...(categoryId ? { categoryId: Number(categoryId) } : {}),
    };
    await onSubmit(body);
  };

  return (
    <form className="card mb-6 space-y-4" onSubmit={handleSubmit} noValidate>
      <h2 className="text-lg font-semibold">
        {initial ? `Редактирование «${initial.name}»` : 'Новый товар'}
      </h2>

      {serverMessage && !Object.keys(serverFields).length && (
        <p role="alert" className="rounded-md bg-danger-50 px-3 py-2 text-sm text-danger-600">
          {serverMessage}
        </p>
      )}

      <div>
        <label htmlFor="p-name" className="mb-1 block text-sm font-medium">Название</label>
        <input
          id="p-name"
          className={`input ${errorFor('name') ? 'input-error' : ''}`}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        {errorFor('name') && <p className="field-error">{errorFor('name')}</p>}
      </div>

      <div>
        <label htmlFor="p-description" className="mb-1 block text-sm font-medium">Описание</label>
        <textarea
          id="p-description"
          className={`input ${errorFor('description') ? 'input-error' : ''}`}
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        {errorFor('description') && <p className="field-error">{errorFor('description')}</p>}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="p-price" className="mb-1 block text-sm font-medium">Цена</label>
          <input
            id="p-price"
            type="number"
            step="0.01"
            min="0"
            className={`input ${errorFor('price') ? 'input-error' : ''}`}
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
          {errorFor('price') && <p className="field-error">{errorFor('price')}</p>}
        </div>

        <div>
          <label htmlFor="p-category" className="mb-1 block text-sm font-medium">Категория</label>
          <select
            id="p-category"
            className={`input ${errorFor('categoryId') ? 'input-error' : ''}`}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">Без категории</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          {errorFor('categoryId') && <p className="field-error">{errorFor('categoryId')}</p>}
        </div>
      </div>

      <div>
        <label htmlFor="p-image" className="mb-1 block text-sm font-medium">Ссылка на изображение</label>
        <input
          id="p-image"
          className={`input ${errorFor('imageUrl') ? 'input-error' : ''}`}
          value={imageUrl}
          onChange={(e) => setImageUrl(e.target.value)}
          placeholder="https://…"
        />
        {errorFor('imageUrl') && <p className="field-error">{errorFor('imageUrl')}</p>}
      </div>

      <div className="flex gap-3">
        <button type="submit" className="btn-primary" disabled={isSaving}>
          {isSaving ? 'Сохраняем…' : 'Сохранить'}
        </button>
        <button type="button" className="btn-ghost" onClick={onCancel} disabled={isSaving}>
          Отмена
        </button>
      </div>
    </form>
  );
}
