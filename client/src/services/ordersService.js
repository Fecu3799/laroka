const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export async function cancelOrder(orderId) {
  const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, { method: 'POST' })
  if (res.status === 422) {
    let message = 'No se puede cancelar el pedido.'
    try {
      const data = await res.json()
      message = data.message || data.error || message
    } catch {}
    const err = new Error(message)
    err.is422 = true
    throw err
  }
  if (!res.ok) throw new Error('Error al cancelar el pedido.')
}
