import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(() => {
  return {
    // Fecha/hora del build inyectada en tiempo de build. La consume sw.js como
    // SW_VERSION: al cambiar en cada deploy, el sw.js compilado deja de ser
    // byte-idéntico y el browser dispara el update del Service Worker.
    define: {
      __SW_BUILD_DATE__: JSON.stringify(new Date().toISOString()),
    },
    plugins: [
      react(),
      VitePWA({
        // injectManifest permite un SW custom (handlers push/notificationclick,
        // US-09-F-03). El runtimeCaching que antes vivía acá ahora está en src/sw.js.
        strategies: 'injectManifest',
        srcDir: 'src',
        filename: 'sw.js',
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
          icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
          ],
        },
        injectManifest: {
          globPatterns: ['**/*.{js,css,html,svg,ico,woff,woff2}'],
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
      include: ['src/**/*.{test,spec}.{js,jsx}'],
    },
  }
})
