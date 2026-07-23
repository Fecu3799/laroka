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

// US-17-05 / US-17-F-02: reintento manual de un reembolso fallido (ADMIN-only,
// backend valida el rol). El endpoint devuelve 204 si el reintento tuvo éxito
// (Payment pasa a REFUNDED) o un error con mensaje si vuelve a fallar.
export async function retryRefund(orderId, token, branchId) {
  await apiFetch(`${API_URL}/backoffice/orders/${orderId}/retry-refund`, {
    method: 'POST',
    headers: backofficeHeaders(token, branchId),
  })
}

// US-19-01 / US-19-02: descuento porcentual manual (MANAGER/ADMIN, el backend
// valida el rol). Devuelve 204; el descuento aplicado llega por el refetch del
// detalle. `silentErrors` porque el modal muestra su propio mensaje por código
// de estado — 400 (rango de porcentaje) y 422 (guard de pago de gateway) tienen
// causas distintas y el toast genérico las mezclaría.
export async function applyDiscount(orderId, { percentage, reason, note }, token, branchId) {
  await apiFetch(
    `${API_URL}/backoffice/orders/${orderId}/discount`,
    {
      method: 'POST',
      headers: backofficeHeaders(token, branchId, { 'Content-Type': 'application/json' }),
      body: JSON.stringify({ percentage, reason, note: note?.trim() ? note.trim() : null }),
    },
    { silentErrors: true },
  )
}
