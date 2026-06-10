import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

function backofficeHeaders(token, branchId, extra = {}) {
  const headers = { Authorization: `Bearer ${token}`, ...extra }
  if (branchId != null) headers['X-Branch-Id'] = String(branchId)
  return headers
}

export async function fetchOrderDetail(orderId, token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/orders/${orderId}`, {
    headers: backofficeHeaders(token, branchId),
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

export async function advanceOrderStatus(orderId, nextStatus, token, branchId) {
  await apiFetch(`${API_URL}/backoffice/orders/${orderId}/status`, {
    method: 'PATCH',
    headers: backofficeHeaders(token, branchId, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ nextStatus }),
  })
}

export async function resolveCancelRequest(orderId, action, token, branchId) {
  await apiFetch(`${API_URL}/backoffice/orders/${orderId}/cancel-request`, {
    method: 'PATCH',
    headers: backofficeHeaders(token, branchId, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ action }),
  })
}
