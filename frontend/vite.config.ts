import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [
    // must come before react()
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  // Playwright owns the browser suites under e2e; Vitest should only run unit/component tests.
  test: {
    include: ['src/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
    exclude: ['e2e/**'],
  },
  // API_PORT must match the port the API listens on (application.yml, default 8080)
  server: { proxy: { '/api': `http://localhost:${process.env.API_PORT ?? '8080'}` } },
})
