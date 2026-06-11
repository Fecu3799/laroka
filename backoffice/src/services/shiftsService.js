import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

function authHeaders(token, branchId) {
  const h = { Authorization: `Bearer ${token}` }
  if (branchId != null) h['X-Branch-Id'] = String(branchId)
  return h
}

export async function getCurrentShift(token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/shifts/current`, {
    headers: authHeaders(token, branchId),
  })
  return res.json()
}

export async function openShift(token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/shifts/open`, {
    method: 'POST',
    headers: authHeaders(token, branchId),
  })
  return res.json()
}

export async function closeShift(token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/shifts/close`, {
    method: 'POST',
    headers: authHeaders(token, branchId),
  })
  return res.json()
}

// Resumen calculado en tiempo real del turno activo (no persiste).
// El backend responde 404 si no hay turno activo.
export async function getCurrentShiftSummary(token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/shifts/current/summary`, {
    headers: authHeaders(token, branchId),
  })
  return res.json()
}

// Alterna la recepción de pedidos de la sucursal. Requiere turno activo
// (el backend responde 422 si no hay turno abierto). Retorna { acceptingOrders }.
export async function toggleAcceptingOrders(token, branchId) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/toggle-orders`, {
    method: 'PATCH',
    headers: authHeaders(token, branchId),
  })
  return res.json()
}

// Historial paginado de turnos cerrados (con su summary embebido).
export async function getShiftHistory(token, branchId, page = 0, size = 20) {
  const res = await apiFetch(
    `${API_URL}/backoffice/shifts/history?page=${page}&size=${size}`,
    { headers: authHeaders(token, branchId) },
  )
  return res.json()
}
