const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// Timeout de seguridad del fetch de suscripción (causa secundaria de cuelgue):
// un stall de red no rechaza hasta el timeout largo del browser. Con AbortController
// la request se cancela y el fetch rechaza; el caller (usePushSubscription) lo traga
// como cualquier otro fallo → retorna null, no lanza.
const PUSH_SUBSCRIBE_TIMEOUT_MS = 3000

/**
 * Registra (upsert) una suscripción Web Push en el backend y devuelve su id (UUID).
 *
 * Usa fetch directo en vez de apiFetch a propósito: el push es best-effort y
 * silencioso, no queremos disparar el toast global de error si la suscripción
 * falla. El caller (usePushSubscription) traga el error y continúa sin push.
 */
export async function upsertPushSubscription(subscription) {
  const { endpoint, keys } = subscription.toJSON()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), PUSH_SUBSCRIBE_TIMEOUT_MS)
  try {
    const res = await fetch(`${API_BASE}/push/subscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        endpoint,
        p256dh: keys?.p256dh,
        auth: keys?.auth,
      }),
      signal: controller.signal,
    })
    if (!res.ok) throw new Error('push_subscribe_failed')
    const data = await res.json()
    return data.id ?? null
  } finally {
    clearTimeout(timer)
  }
}
