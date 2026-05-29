import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchOrderDetail(orderId, token) {
  const res = await apiFetch(`${API_URL}/backoffice/orders/${orderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

export async function createBackofficeOrder(data, token) {
  const res = await apiFetch(`${API_URL}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  })
  return res.json()
}

export async function advanceOrderStatus(orderId, nextStatus, token) {
  await apiFetch(`${API_URL}/backoffice/orders/${orderId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ nextStatus }),
  })
}

export async function resolveCancelRequest(orderId, action, token) {
  await apiFetch(`${API_URL}/backoffice/orders/${orderId}/cancel-request`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ action }),
  })
}
