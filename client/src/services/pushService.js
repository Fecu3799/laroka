const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * Registra (upsert) una suscripción Web Push en el backend y devuelve su id (UUID).
 *
 * Usa fetch directo en vez de apiFetch a propósito: el push es best-effort y
 * silencioso, no queremos disparar el toast global de error si la suscripción
 * falla. El caller (usePushSubscription) traga el error y continúa sin push.
 */
export async function upsertPushSubscription(subscription) {
  const { endpoint, keys } = subscription.toJSON()
  const res = await fetch(`${API_BASE}/push/subscribe`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      endpoint,
      p256dh: keys?.p256dh,
      auth: keys?.auth,
    }),
  })
  if (!res.ok) throw new Error('push_subscribe_failed')
  const data = await res.json()
  return data.id ?? null
}
