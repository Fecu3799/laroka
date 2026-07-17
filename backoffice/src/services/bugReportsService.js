const API_URL = import.meta.env.VITE_API_URL ?? ''

function authHeaders(token, branchId) {
  const h = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
  if (branchId != null) h['X-Branch-Id'] = String(branchId)
  return h
}

// fetch directo (no apiFetch) a propósito: US-17-F-03 exige feedback con copy
// específico ("Reporte enviado" / "No se pudo enviar, intentá de nuevo.") manejado
// por el modal, no el toast genérico global que dispara apiFetch ante un error.
export async function sendBugReport({ description, url, userAgent }, token, branchId) {
  const res = await fetch(`${API_URL}/backoffice/bug-reports`, {
    method: 'POST',
    headers: authHeaders(token, branchId),
    body: JSON.stringify({ description, url, userAgent }),
  })
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`)
    err.status = res.status
    throw err
  }
}
