import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach } from 'vitest';
import { cleanup } from '@testing-library/react';

/**
 * Deterministic in-memory localStorage for tests.
 *
 * Why we install our own instead of relying on jsdom: Node 25 ships an experimental
 * global Web Storage (`--localstorage-file`) that shadows jsdom's `window.localStorage`
 * and is non-functional without a backing file. A simple Map-backed Storage is reliable
 * and isolated, which is exactly what the auth token-storage tests need.
 */
class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

beforeEach(() => {
  Object.defineProperty(window, 'localStorage', {
    value: new MemoryStorage(),
    writable: true,
    configurable: true,
  });
});

// RTL does not auto-clean between tests under Vitest globals — unmount rendered trees
// and reset storage so each test is isolated.
afterEach(() => {
  cleanup();
  window.localStorage.clear();
});
