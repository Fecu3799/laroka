import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchBranches(token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Datos públicos de una sucursal (incluye acceptingOrders).
export async function fetchBranch(token, branchId) {
  const res = await apiFetch(`${API_URL}/branches/${branchId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Listado de backoffice — incluye maxShiftDurationMinutes y demás config.
// Se filtra por tenant del ADMIN autenticado.
export async function fetchBackofficeBranches(token, tenantId) {
  const qs = tenantId != null ? `?tenantId=${tenantId}` : ''
  const res = await apiFetch(`${API_URL}/backoffice/branches${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Actualiza la configuración de una sucursal (ADMIN). maxShiftDurationMinutes en minutos.
export async function updateBranchConfig(branchId, maxShiftDurationMinutes, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/config`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ maxShiftDurationMinutes }),
  })
  return res.json()
}

// Horario semanal de una sucursal (ADMIN). Siempre 7 días (MON..SUN).
export async function fetchBranchSchedule(branchId, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/schedule`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Upsert del horario semanal. days: lista de 7 días con horas en formato HH:mm.
export async function saveBranchSchedule(branchId, days, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/schedule`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(days),
  })
  return res.json()
}
