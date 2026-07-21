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

// Tipos de categoría maestros activos (US-CAT-03), ordenados por nombre. Cada uno:
// { id, name, allowsHalfAndHalf }. ADMIN y MANAGER pueden listar.
export async function fetchCategoryTypes(token) {
  const res = await apiFetch(`${API_URL}/backoffice/category-types`, {
    headers: catalogHeaders(token),
  })
  return res.json()
}

// data: { name, tenantId, categoryTypeId }. Exclusivo ADMIN.
export async function createCategory(data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/categories`, {
    method: 'POST',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { name, tenantId, categoryTypeId }. Exclusivo ADMIN.
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

// Disponibilidad inline del producto en la sucursal del MANAGER (US-14-F-04). El
// backend resuelve el branchId desde el token, por eso no se envía X-Branch-Id.
export async function updateProductAvailability(productId, available, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/availability`, {
    method: 'PATCH',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ available }),
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

// ── Tamaños de producto (US-SIZE-04) ─────────────────────────────────────────
//
// Sólo existe el tamaño CHICA: el grande es implícito y su precio es siempre el precio
// base del producto. El backend rechaza con 422 cualquier intento de crear GRANDE.

// Devuelve todos los tamaños del producto, activos e inactivos. ADMIN y MANAGER.
export async function fetchProductSizes(productId, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/sizes`, {
    headers: catalogHeaders(token),
  })
  return res.json()
}

// data: { size, price }. Exclusivo ADMIN.
export async function createProductSize(productId, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/sizes`, {
    method: 'POST',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { price?, active? } — se modifica sólo lo que se envía. Exclusivo ADMIN.
// active:false es baja lógica: la fila se conserva por los pedidos históricos.
export async function updateProductSize(productId, sizeId, data, token) {
  const res = await apiFetch(`${API_URL}/backoffice/products/${productId}/sizes/${sizeId}`, {
    method: 'PATCH',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
  return res.json()
}

// data: { branchId, priceOverride }. ADMIN y MANAGER. priceOverride null limpia el
// override y el tamaño vuelve a su precio base. Responde 204 sin cuerpo.
export async function updateProductSizeBranchConfig(productId, sizeId, data, token) {
  await apiFetch(`${API_URL}/backoffice/products/${productId}/sizes/${sizeId}/branch-config`, {
    method: 'PATCH',
    headers: catalogHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
  })
}
