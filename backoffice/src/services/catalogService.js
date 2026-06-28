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

// Productos del tenant (US-14-F-02). ADMIN y MANAGER pueden listar.
export async function fetchProducts(token, tenantId) {
  const url = tenantId != null
    ? `${API_URL}/backoffice/products?tenantId=${tenantId}`
    : `${API_URL}/backoffice/products`
  const res = await apiFetch(url, { headers: catalogHeaders(token) })
  return res.json()
}

// data: { name, description, price, imageUrl, categoryId, tenantId }. Exclusivo ADMIN.
export async function createProduct(data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products`, {
    method: 'POST',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { name, description, price, imageUrl, categoryId, tenantId }. Exclusivo ADMIN.
export async function updateProduct(id, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${id}`, {
    method: 'PUT',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// Exclusivo ADMIN.
export async function deleteProduct(id, token) {
  await apiFetch(`${API_URL}/backoffice/products/${id}`, {
    method: 'DELETE',
    headers: catalogHeaders(token),
  })
}

// Configuración por sucursal de un producto (US-14-F-03). Devuelve una fila por
// sucursal del tenant: { branchId, branchName, available, priceOverride (nullable),
// effectivePrice }. ADMIN y MANAGER pueden consultar.
export async function fetchProductBranchConfig(productId, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/branch-config`, {
    headers: catalogHeaders(token),
  })
  return res.json()
}

// data: { branchId, priceOverride?, available? }. ADMIN y MANAGER.
// priceOverride null limpia el override (vuelve al precio base); available null no
// modifica la disponibilidad. Importante: el backend siempre reescribe priceOverride,
// por lo que al togglear disponibilidad hay que enviar el override vigente para no perderlo.
export async function updateProductBranchConfig(productId, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/branch-config`, {
    method: 'PATCH',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { price, applyToAllBranches }. Exclusivo ADMIN. Con applyToAllBranches true
// además limpia el override de todas las sucursales, igualando el precio.
export async function updateProductPrice(productId, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/price`, {
    method: 'PUT',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}
