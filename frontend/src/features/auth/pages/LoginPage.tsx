import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useLoginMutation } from '@/features/auth/authApi';
import { parseApiError } from '@/shared/lib/apiError';
import { logger } from '@/shared/lib/logger';

interface LocationState {
  from?: string;
}

/** Login page. On success the authApi onQueryStarted stores the session; we then redirect. */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [login, { isLoading, error }] = useLoginMutation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);

  const apiError = error ? parseApiError(error) : null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setClientError(null);

    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setClientError('Введите корректный email.');
      return;
    }
    if (password.length < 8) {
      setClientError('Пароль должен быть не короче 8 символов.');
      return;
    }

    logger.debug('[login] submit', { email }); // never log the password
    try {
      await login({ email, password }).unwrap();
      const to = (location.state as LocationState | null)?.from ?? '/';
      navigate(to, { replace: true });
    } catch {
      // Error surfaced via the `error` from the mutation hook.
      logger.warn('[login] rejected');
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-bold">Вход</h1>
      <form className="card space-y-4" onSubmit={handleSubmit} noValidate>
        {(clientError || apiError) && (
          <p role="alert" className="rounded-md bg-danger-50 px-3 py-2 text-sm text-danger-600">
            {clientError ?? apiError?.message}
          </p>
        )}

        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium">
            Email
          </label>
          <input
            id="email"
            type="email"
            className={`input ${apiError?.fields.email ? 'input-error' : ''}`}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
          {apiError?.fields.email && <p className="field-error">{apiError.fields.email}</p>}
        </div>

        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">
            Пароль
          </label>
          <input
            id="password"
            type="password"
            className={`input ${apiError?.fields.password ? 'input-error' : ''}`}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
          {apiError?.fields.password && <p className="field-error">{apiError.fields.password}</p>}
        </div>

        <button type="submit" className="btn-primary w-full" disabled={isLoading}>
          {isLoading ? 'Вход…' : 'Войти'}
        </button>

        <p className="text-center text-sm text-slate-500">
          Нет аккаунта?{' '}
          <Link to="/register" className="text-brand-600 hover:underline">
            Зарегистрироваться
          </Link>
        </p>
      </form>
    </div>
  );
}
