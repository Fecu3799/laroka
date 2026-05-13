const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function advanceOrderStatus(orderId, nextStatus, token) {
  const res = await fetch(`${API_URL}/backoffice/orders/${orderId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ nextStatus }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
