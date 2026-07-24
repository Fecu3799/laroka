const API_URL = import.meta.env.VITE_API_URL ?? ''

function dispatchToast(message) {
  window.dispatchEvent(new CustomEvent('pedisur:toast', { detail: { message } }))
}

async function tryRefresh() {
  const refreshToken = localStorage.getItem('pedisur_refresh_token')
  if (!refreshToken) return null
  try {
    const res = await fetch(`${API_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return null
    const data = await res.json()
    localStorage.setItem('pedisur_token', data.token)
    if (data.refreshToken) localStorage.setItem('pedisur_refresh_token', data.refreshToken)
    return data.token
  } catch {
    return null
  }
}

/**
 * `silentErrors` suprime los toasts automáticos y deja el error entero en manos
 * del caller. Es para pantallas que muestran el error inline y necesitan un
 * mensaje propio por código de estado: el toast genérico las contradiría (un 400
 * de @Valid llega con message "Validation failed", que no le dice nada al
 * operador). El error se sigue lanzando con `.status`, así que el caller decide
 * qué texto mostrar. El refresh de token en 401 y la redirección al login no se
 * ven afectados.
 */
export async function apiFetch(url, options = {}, { silentErrors = false } = {}) {
  const toast = silentErrors ? () => {} : dispatchToast
  let res
  try {
    res = await fetch(url, options)
  } catch {
    toast('Sin conexión. Verificá tu internet.')
    throw new Error('network_error')
  }

  if (res.ok) return res

  if (res.status === 401) {
    const newToken = await tryRefresh()
    if (newToken) {
      try {
        res = await fetch(url, {
          ...options,
          headers: { ...options.headers, Authorization: `Bearer ${newToken}` },
        })
        if (res.ok) return res
      } catch {
        toast('Sin conexión. Verificá tu internet.')
        throw new Error('network_error')
      }
    }
    localStorage.removeItem('pedisur_token')
    localStorage.removeItem('pedisur_refresh_token')
    window.location.href = '/login?reason=expired'
    throw new Error('session_expired')
  }

  let message = null
  try {
    const body = await res.json()
    message = body?.message ?? null
  } catch { /* ignore */ }

  if (res.status >= 500) {
    toast('Ocurrió un error. Intentá de nuevo.')
  } else {
    toast(message ?? 'Error al procesar la solicitud.')
  }

  const err = new Error(message ?? `HTTP ${res.status}`)
  err.status = res.status
  throw err
}
