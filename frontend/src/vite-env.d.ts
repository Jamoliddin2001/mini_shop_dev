/// <reference types="vite/client" />

// Typed access to our Vite env vars (import.meta.env.VITE_*).
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
