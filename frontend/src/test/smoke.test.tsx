import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from './renderWithProviders';

// Smoke test: confirms the Vitest + jsdom + RTL + jest-dom harness is wired correctly,
// including a working jsdom localStorage (needed by the auth token storage in Task 7).
describe('test harness', () => {
  it('renders a component through the providers', () => {
    renderWithProviders(<p>harness ok</p>);
    expect(screen.getByText('harness ok')).toBeInTheDocument();
  });

  it('provides a working localStorage', () => {
    window.localStorage.setItem('k', 'v');
    expect(window.localStorage.getItem('k')).toBe('v');
  });
});
