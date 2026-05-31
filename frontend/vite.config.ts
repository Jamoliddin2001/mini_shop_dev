import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Vite + Vitest config. The `@` alias mirrors tsconfig `paths` so imports resolve
// identically in the bundler, the type-checker, and the test runner.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173, // must match backend CORS_ALLOWED_ORIGINS (application.yml)
  },
  test: {
    // happy-dom (not jsdom): jsdom's AbortSignal is incompatible with Node's undici
    // `Request`, which breaks RTK Query's fetchBaseQuery. happy-dom is undici-compatible.
    environment: 'happy-dom',
    globals: true,
    setupFiles: ['./src/test/setupTests.ts'],
    css: true,
    // Tests do not load .env files; provide the API base URL fetchBaseQuery needs.
    env: {
      VITE_API_BASE_URL: 'http://localhost:8080/api',
    },
  },
});
