function dispatchToast(message) {
  window.dispatchEvent(new CustomEvent('laroka:toast', { detail: { message } }))
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

  let message = null
  try {
    const body = await res.json()
    message = body?.message ?? null
  } catch {}

  if (res.status >= 500) {
    dispatchToast('Ocurrió un error. Intentá de nuevo.')
  } else {
    dispatchToast(message ?? 'Error al procesar la solicitud.')
  }

  const err = new Error(message ?? `HTTP ${res.status}`)
  err.status = res.status
  throw err
}
