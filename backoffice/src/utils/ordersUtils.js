export const STATUS_CONFIG = {
  PENDING_PAYMENT:  { label: 'Pago pendiente', bg: '#6b672e', color: '#c5cda7', border: '#4a6b50' },
  RECEIVED:         { label: 'Recibido',       bg: '#1d3557', color: '#90bdf9', border: '#2a4a80' },
  IN_PREPARATION:   { label: 'En preparación', bg: '#2d1f00', color: '#fbbf24', border: '#5c3d00' },
  ON_THE_WAY:       { label: 'En camino',      bg: '#2d1047', color: '#c084fc', border: '#5a1f8a' },
  READY_FOR_PICKUP: { label: 'Para retirar',   bg: '#1e0a3a', color: '#a78bfa', border: '#4c1d95' },
  DELIVERED:        { label: 'Entregado',      bg: '#0a2e14', color: '#4ade80', border: '#1a5c2c' },
  CANCELLED:        { label: 'Cancelado',      bg: '#2e0f0f', color: '#f87171', border: '#5c1f1f' },
}

export const STATUS_PRIORITY = {
  CANCELLATION_REQUESTED: 0,
  RECEIVED:               1,
  IN_PREPARATION:         2,
  ON_THE_WAY:             3,
  READY_FOR_PICKUP:       3,
  DELIVERED:              4,
  CANCELLED:              4,
  PENDING_PAYMENT:        5,
}

export function sortOrders(orders) {
  return [...orders].sort((a, b) => {
    const pa = STATUS_PRIORITY[a.status] ?? 99
    const pb = STATUS_PRIORITY[b.status] ?? 99
    if (pa !== pb) return pa - pb
    return new Date(b.createdAt) - new Date(a.createdAt)
  })
}

export function getNextStatus(status, orderType) {
  if (status === 'RECEIVED')       return 'IN_PREPARATION'
  if (status === 'IN_PREPARATION') return orderType === 'DELIVERY' ? 'ON_THE_WAY' : 'READY_FOR_PICKUP'
  if (status === 'ON_THE_WAY' || status === 'READY_FOR_PICKUP') return 'DELIVERED'
  return null
}

export function canGoBack(status) {
  return ['IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP'].includes(status)
}

export function canCancel(status) {
  return status === 'RECEIVED'
}

export function canConfirmOrder({ cartItems, orderType, deliveryAddress }) {
  return cartItems.length > 0 &&
    (orderType !== 'DELIVERY' || (deliveryAddress ?? '').trim().length > 0)
}
