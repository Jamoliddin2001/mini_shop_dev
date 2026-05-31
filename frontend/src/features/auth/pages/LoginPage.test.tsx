import { describe, it, expect, vi, afterEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import LoginPage from '@/features/auth/pages/LoginPage';

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function LoginRoutes() {
  return (
    <Routes>
      <Route path="/" element={<p>catalog home</p>} />
      <Route path="/login" element={<LoginPage />} />
    </Routes>
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('LoginPage', () => {
  it('logs in, stores the token and redirects home', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          { accessToken: 'jwt-xyz', tokenType: 'Bearer', expiresIn: 3600, email: 'a@shop.local', role: 'USER' },
          200,
        ),
      ),
    );
    const user = userEvent.setup();
    const { store } = renderWithProviders(<LoginRoutes />, { route: '/login' });

    await user.type(screen.getByLabelText('Email'), 'a@shop.local');
    await user.type(screen.getByLabelText('Пароль'), 'Secret123');
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    await waitFor(() => expect(screen.getByText('catalog home')).toBeInTheDocument());
    expect(store.getState().auth.token).toBe('jwt-xyz');
  });

  it('shows the ApiError message on invalid credentials', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(
          {
            timestamp: '2026-05-30T10:00:00Z',
            status: 401,
            error: 'Unauthorized',
            message: 'Неверный email или пароль',
            path: '/api/auth/login',
          },
          401,
        ),
      ),
    );
    const user = userEvent.setup();
    const { store } = renderWithProviders(<LoginRoutes />, { route: '/login' });

    await user.type(screen.getByLabelText('Email'), 'a@shop.local');
    await user.type(screen.getByLabelText('Пароль'), 'WrongPass1');
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный email или пароль');
    expect(store.getState().auth.token).toBeNull();
  });

  it('blocks submit with a client-side validation message for a bad email', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderWithProviders(<LoginRoutes />, { route: '/login' });

    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.type(screen.getByLabelText('Пароль'), 'Secret123');
    await user.click(screen.getByRole('button', { name: 'Войти' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Введите корректный email');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
