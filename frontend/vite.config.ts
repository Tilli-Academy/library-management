/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// Where the Spring Boot backend runs during development.
const backendUrl = process.env.PAGETURN_BACKEND_URL ?? 'http://127.0.0.1:8097'

// URL prefix the app is served under: "/" normally, "/absproxy/5177/" through code-server (npm run dev:remote).
const base = process.env.PAGETURN_BASE_PATH ?? '/'

function backendProxy(): ProxyOptions {
  return {
    target: backendUrl,
    // /absproxy/5177/api/... -> /api/...
    rewrite: (path) => path.slice(base.length - 1),
    // The browser only talks to this dev server (same origin), so CORS is never involved.
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
    },
  }
}

export default defineConfig({
  base,
  plugins: [
    react(),
    tailwindcss(),
    // Installable app ("Add to home screen") + offline shell.
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'script', // external registerSW.js file: works with a strict Content-Security-Policy
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'PageTurn Library',
        short_name: 'PageTurn',
        description: 'Browse the library catalogue and keep track of the books you borrowed.',
        theme_color: '#6d28d9',
        background_color: '#faf7f2',
        display: 'standalone',
        start_url: '.',
        scope: '.',
        icons: [
          { src: 'pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Only the app's own files are cached. API responses (personal data) are NEVER stored offline.
        globPatterns: ['**/*.{js,css,html,svg,png,webmanifest}'],
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [/\/api\//, /\/actuator\//],
        runtimeCaching: [],
        cleanupOutdatedCaches: true,
      },
    }),
  ],
  build: {
    // Never publish source maps (they reveal the original source code).
    sourcemap: false,
    // The main bundle (React, router, forms) is ~505 kB raw / ~157 kB gzipped — normal for a React app.
    // Librarian screens are split into separate files. Warn if the main bundle grows beyond this.
    chunkSizeWarningLimit: 560,
  },
  server: {
    port: 5177,
    strictPort: true,
    host: '127.0.0.1',
    proxy: {
      [`${base}api`]: backendProxy(),
    },
  },
  preview: {
    port: 5178,
    strictPort: true,
    host: '127.0.0.1',
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
