import type { Config } from 'tailwindcss';

// Theme tokens for the mini-shop. Kept intentionally small (KISS): a brand scale,
// neutral surfaces and a danger color reused by forms/buttons across features.
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef6ff',
          100: '#d9eaff',
          500: '#2f6fed',
          600: '#1f59d0',
          700: '#1846a8',
        },
        surface: {
          DEFAULT: '#ffffff',
          muted: '#f5f7fa',
          border: '#e2e8f0',
        },
        danger: {
          50: '#fef2f2',
          500: '#dc2626',
          600: '#b91c1c',
        },
      },
      borderRadius: {
        card: '0.75rem',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
