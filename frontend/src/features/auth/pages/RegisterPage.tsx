import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useRegisterMutation } from '@/features/auth/authApi';
import { parseApiError } from '@/shared/lib/apiError';
import { logger } from '@/shared/lib/logger';

/** Registration page. Backend returns 201 with no body; we then send the user to /login. */
export default function RegisterPage() {
  const navigate = useNavigate();
  const [register, { isLoading, error }] = useRegisterMutation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);

  const apiError = error ? parseApiError(error) : null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setClientError(null);

    if (!/^\S+@\S+\.\S+$/.test(email)) {
      setClientError('Введите корректный email.');
      return;
    }
    // Mirrors backend RegisterRequest: password 8..72 chars (BCrypt cap).
    if (password.length < 8 || password.length > 72) {
      setClientError('Пароль должен быть от 8 до 72 символов.');
      return;
    }
    if (password !== confirm) {
      setClientError('Пароли не совпадают.');
      return;
    }

    logger.debug('[register] submit', { email }); // never log the password
    try {
      await register({ email, password }).unwrap();
      navigate('/login', { replace: true });
    } catch {
      logger.warn('[register] rejected');
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-bold">Регистрация</h1>
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
            autoComplete="new-password"
            required
          />
          {apiError?.fields.password && <p className="field-error">{apiError.fields.password}</p>}
        </div>

        <div>
          <label htmlFor="confirm" className="mb-1 block text-sm font-medium">
            Повторите пароль
          </label>
          <input
            id="confirm"
            type="password"
            className="input"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            required
          />
        </div>

        <button type="submit" className="btn-primary w-full" disabled={isLoading}>
          {isLoading ? 'Регистрация…' : 'Зарегистрироваться'}
        </button>

        <p className="text-center text-sm text-slate-500">
          Уже есть аккаунт?{' '}
          <Link to="/login" className="text-brand-600 hover:underline">
            Войти
          </Link>
        </p>
      </form>
    </div>
  );
}
