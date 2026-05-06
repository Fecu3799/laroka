import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_URL || 'http://localhost:8080'
  const escapedApiBase = apiBase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

  return {
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        manifest: {
          name: 'LaRoka Pizzería',
          short_name: 'LaRoka',
          description: 'Menú de LaRoka Pizzería',
          theme_color: '#095E2F',
          background_color: '#000000',
          display: 'standalone',
          start_url: '/',
          scope: '/',
        },
        workbox: {
          runtimeCaching: [
            {
              urlPattern: ({ request }) => request.destination === 'image',
              handler: 'CacheFirst',
              options: {
                cacheName: 'laroka-images',
                expiration: {
                  maxEntries: 150,
                  maxAgeSeconds: 7 * 24 * 60 * 60,
                },
              },
            },
            {
              urlPattern: new RegExp(`^${escapedApiBase}`),
              handler: 'NetworkFirst',
              options: {
                cacheName: 'laroka-api',
                networkTimeoutSeconds: 10,
                expiration: {
                  maxEntries: 50,
                  maxAgeSeconds: 5 * 60,
                },
              },
            },
          ],
        },
      }),
    ],
    server: {
      host: true,
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.js',
      css: false,
    },
  }
})
