import { apiFetch } from './http'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export async function cancelOrder(orderId) {
  await apiFetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' })
}
