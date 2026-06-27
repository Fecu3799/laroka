import { TENANT_ID } from '../config'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// Obtiene el perfil público del tenant (GET /tenants/{id}/profile).
// Devuelve el perfil si existe (200), o null si no hay perfil configurado (404).
// Usa fetch directo en lugar de apiFetch para que un 404 esperado no dispare
// un toast de error al usuario.
export async function getTenantProfile() {
  const res = await fetch(`${API_BASE}/tenants/${encodeURIComponent(TENANT_ID)}/profile`)
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}
