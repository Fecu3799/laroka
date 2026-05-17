const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function fetchOrderDetail(orderId, token) {
  const res = await fetch(`${API_URL}/backoffice/orders/${orderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

export async function createBackofficeOrder(data, token) {
  const res = await fetch(`${API_URL}/orders`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

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
