import { apiFetch } from './http'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export async function cancelOrder(orderId, reason) {
  await apiFetch(`${API_BASE}/orders/${orderId}/cancel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason: reason ?? null }),
  })
}
