// Identificación del tenant para la PWA del cliente.
//
// VITE_TENANT_ID se inyecta en tiempo de build (Vercel / .env.local) e indica
// a qué tenant pertenecen las sucursales que sirve este cliente. Se usa para
// filtrar GET /branches?tenantId=... El prefijo VITE_ es obligatorio para que
// Vite la exponga vía import.meta.env en el navegador.
const rawTenantId = import.meta.env.VITE_TENANT_ID

export const TENANT_ID = rawTenantId

// true sólo si la variable está definida y no es una cadena vacía/espacios.
export const isTenantConfigured =
  rawTenantId !== undefined &&
  rawTenantId !== null &&
  String(rawTenantId).trim() !== ''
