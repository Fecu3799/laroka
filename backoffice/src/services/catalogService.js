import { apiFetch } from './http'

const API_URL = import.meta.env.VITE_API_URL ?? ''

// Categorías y productos son recursos del tenant, no de una sucursal: no llevan
// X-Branch-Id (igual que staffService). El tenant se resuelve por el token / tenantId.
function catalogHeaders(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra }
}

export async function fetchBranchMenu(branchId, token) {
  const res = await apiFetch(`${API_URL}/branches/${branchId}/menu`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.json()
}

// Categorías del tenant, ordenadas por nombre, cada una con productCount (US-14-05).
export async function fetchCategories(token, tenantId) {
  const url = tenantId != null
    ? `${API_URL}/backoffice/categories?tenantId=${tenantId}`
    : `${API_URL}/backoffice/categories`
  const res = await apiFetch(url, { headers: catalogHeaders(token) })
  return res.json()
}

// data: { name, tenantId }. Exclusivo ADMIN.
export async function createCategory(data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/categories`, {
    method: 'POST',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { name, tenantId }. Exclusivo ADMIN.
export async function updateCategory(id, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/categories/${id}`, {
    method: 'PUT',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// Exclusivo ADMIN.
export async function deleteCategory(id, token) {
  await apiFetch(`${API_URL}/backoffice/categories/${id}`, {
    method: 'DELETE',
    headers: catalogHeaders(token),
  })
}
