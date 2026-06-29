import { precacheAndRoute } from 'workbox-precaching'
import { registerRoute } from 'workbox-routing'
import { CacheFirst, NetworkFirst } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { clientsClaim } from 'workbox-core'

// ─── Versión del Service Worker ──────────────────────────────────────────────
// El SW liga su CSP (la que gobierna sus fetch() de runtime caching) al momento
// en que se instala. Cuando cambia algo fuera del build —p. ej. la CSP en
// vercel.json— el sw.js compilado queda byte-idéntico y el navegador NO dispara
// un update, dejando a los clientes con la CSP vieja. Bumpear esta constante
// fuerza un sw.js distinto → update → reinstalación con la CSP nueva.
/* global __SW_BUILD_DATE__ -- inyectada por Vite (define) en tiempo de build */
const SW_VERSION = __SW_BUILD_DATE__
console.info('[SW] version', SW_VERSION)

// ─── Precache ────────────────────────────────────────────────────────────────
// vite-plugin-pwa (injectManifest) reemplaza self.__WB_MANIFEST por la lista de
// assets buildeados (js/css/html). Mantiene el precache que tenía generateSW.
precacheAndRoute(self.__WB_MANIFEST)

// ─── Runtime caching ─────────────────────────────────────────────────────────
// En injectManifest el runtimeCaching del config no aplica; se reimplementa acá
// con las mismas estrategias/expiraciones que tenía la config previa.

// Imágenes → CacheFirst
registerRoute(
  ({ request }) => request.destination === 'image',
  new CacheFirst({
    cacheName: 'laroka-images',
    plugins: [
      new ExpirationPlugin({ maxEntries: 150, maxAgeSeconds: 7 * 24 * 60 * 60 }),
    ],
  }),
)

// API → NetworkFirst
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
registerRoute(
  ({ url }) => url.href.startsWith(API_BASE),
  new NetworkFirst({
    cacheName: 'laroka-api',
    networkTimeoutSeconds: 10,
    plugins: [
      new ExpirationPlugin({ maxEntries: 50, maxAgeSeconds: 5 * 60 }),
    ],
  }),
)

// ─── Activación inmediata (equivale a registerType: 'autoUpdate') ─────────────
self.skipWaiting()
clientsClaim()

// ─── Web Push (US-09-F-03) ───────────────────────────────────────────────────
// Muestra una notificación nativa aunque la app esté cerrada.
self.addEventListener('push', (event) => {
  let payload
  try {
    payload = event.data ? event.data.json() : null
  } catch (err) {
    console.error('[SW] push payload no parseable', err)
    return
  }

  if (!payload || !payload.title) {
    console.warn('[SW] push sin payload válido, no se muestra notificación')
    return
  }

  const { title, body, orderId } = payload
  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: '/icon-192.png',
      data: { orderId },
    }),
  )
})

// ─── Click en la notificación ────────────────────────────────────────────────
// Enfoca una ventana de tracking ya abierta o abre /tracking?orderId={orderId}.
self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const orderId = event.notification.data?.orderId
  const targetUrl = new URL(`/tracking?orderId=${orderId}`, self.location.origin).href

  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        for (const client of windowClients) {
          if (client.url.includes('/tracking') && 'focus' in client) {
            return client.focus()
          }
        }
        if (self.clients.openWindow) {
          return self.clients.openWindow(targetUrl)
        }
        return undefined
      }),
  )
})
