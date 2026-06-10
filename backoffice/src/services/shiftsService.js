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
