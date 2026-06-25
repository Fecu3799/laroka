import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

function staffHeaders(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra }
}

// Tenant-wide listing — el backend resuelve el tenant del token ADMIN.
export async function fetchStaffUsers(token) {
  const res = await apiFetch(`${API_URL}/backoffice/staff-users`, {
    headers: staffHeaders(token),
  })
  return res.json()
}

// data: { name, password, role, branchId }. El email se genera en el backend.
export async function createStaffUser(data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/staff-users`, {
    method: 'POST',
    headers: staffHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { name, role, branchId }.
export async function updateStaffUser(id, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/staff-users/${id}`, {
    method: 'PATCH',
    headers: staffHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

export async function resetStaffUserPassword(id, newPassword, token) {
  await apiFetch(`${API_URL}/backoffice/staff-users/${id}/password`, {
    method: 'PATCH',
    headers: staffHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ newPassword }),
  })
}

export async function setStaffUserStatus(id, active, token) {
  await apiFetch(`${API_URL}/backoffice/staff-users/${id}/status`, {
    method: 'PATCH',
    headers: staffHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ active }),
  })
}
