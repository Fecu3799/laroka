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

// Actualiza la configuración de una sucursal (ADMIN). config incluye
// maxShiftDurationMinutes (minutos, obligatorio) y los campos opcionales de patch
// parcial: name, address, phone, deliveryFee, serviceFee, estimatedDeliveryMinutes.
export async function updateBranchConfig(branchId, config, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/config`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  })
  return res.json()
}

// Crea una sucursal (ADMIN). payload: name, address, phone, deliveryFee, serviceFee,
// estimatedDeliveryMinutes, tenantId. maxShiftDurationMinutes usa el default del backend.
export async function createBranch(payload, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return res.json()
}

// Activa/desactiva una sucursal (ADMIN, US-15-04). Al desactivar con un turno
// abierto el backend responde 400; apiFetch propaga ese mensaje en err.message.
export async function setBranchStatus(branchId, active, token) {
  await apiFetch(`${API_URL}/backoffice/branches/${branchId}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ active }),
  })
}

// Lista todos los productos con su disponibilidad para una sucursal (US-15-08).
// Incluye disponibles y no disponibles, con categoría, para el checklist de gestión.
export async function fetchBranchProducts(branchId, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/products`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Actualización masiva de disponibilidad (US-15-07). Setea `available` para todos los
// productId de la lista en esa sucursal. Una sucursal desactivada responde 422; apiFetch
// propaga ese mensaje en err.message. Retorna { updated }.
export async function updateBranchProductsAvailability(branchId, productIds, available, token) {
  const res = await apiFetch(`${API_URL}/backoffice/branches/${branchId}/products/availability`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ productIds, available }),
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
