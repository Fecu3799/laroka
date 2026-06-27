import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

// Perfil del negocio del tenant del ADMIN autenticado.
// 404 (todavía no existe) → null, sin toast: es el estado inicial esperado,
// por eso usa fetch directo en lugar de apiFetch.
export async function fetchTenantProfile(token) {
  const res = await fetch(`${API_URL}/backoffice/tenant/profile`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

// Upsert (crear o actualizar) del perfil del negocio.
export async function saveTenantProfile(profile, token) {
  const res = await apiFetch(`${API_URL}/backoffice/tenant/profile`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(profile),
  })
  return res.json()
}
