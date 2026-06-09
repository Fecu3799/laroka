const API_URL = import.meta.env.VITE_API_URL ?? ''

function dispatchToast(message) {
  window.dispatchEvent(new CustomEvent('laroka:toast', { detail: { message } }))
}

async function tryRefresh() {
  const refreshToken = localStorage.getItem('laroka_refresh_token')
  if (!refreshToken) return null
  try {
    const res = await fetch(`${API_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return null
    const data = await res.json()
    localStorage.setItem('laroka_token', data.token)
    if (data.refreshToken) localStorage.setItem('laroka_refresh_token', data.refreshToken)
    return data.token
  } catch {
    return null
  }
}

export async function apiFetch(url, options = {}) {
  let res
  try {
    res = await fetch(url, options)
  } catch {
    dispatchToast('Sin conexión. Verificá tu internet.')
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
        dispatchToast('Sin conexión. Verificá tu internet.')
        throw new Error('network_error')
      }
    }
    localStorage.removeItem('laroka_token')
    localStorage.removeItem('laroka_refresh_token')
    window.location.href = '/login?reason=expired'
    throw new Error('session_expired')
  }

  let message = null
  try {
    const body = await res.json()
    message = body?.message ?? null
  } catch { /* ignore */ }

  if (res.status >= 500) {
    dispatchToast('Ocurrió un error. Intentá de nuevo.')
  } else {
    dispatchToast(message ?? 'Error al procesar la solicitud.')
  }

  const err = new Error(message ?? `HTTP ${res.status}`)
  err.status = res.status
  throw err
}
