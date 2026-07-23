export const STATUS_CONFIG = {
  PENDING_PAYMENT:         { label: 'Pago pendiente',   bg: '#6b672e', color: '#c5cda7', border: '#4a6b50' },
  RECEIVED:                { label: 'Recibido',         bg: '#1d3557', color: '#90bdf9', border: '#2a4a80' },
  IN_PREPARATION:          { label: 'En preparación',   bg: '#2d1f00', color: '#fbbf24', border: '#5c3d00' },
  CANCELLATION_REQUESTED:  { label: 'Canc. solicitada', bg: '#3d1a00', color: '#fb923c', border: '#7c3a00' },
  ON_THE_WAY:              { label: 'En camino',        bg: '#2d1047', color: '#c084fc', border: '#5a1f8a' },
  READY_FOR_PICKUP:        { label: 'Para retirar',     bg: '#1e0a3a', color: '#a78bfa', border: '#4c1d95' },
  DELIVERED:               { label: 'Entregado',        bg: '#0a2e14', color: '#4ade80', border: '#1a5c2c' },
  CANCELLED:               { label: 'Cancelado',        bg: '#2e0f0f', color: '#f87171', border: '#5c1f1f' },
}

export const STATUS_PRIORITY = {
  CANCELLATION_REQUESTED: 0,
  PENDING_PAYMENT:        1,
  RECEIVED:               2,
  IN_PREPARATION:         3,
  ON_THE_WAY:             4,
  READY_FOR_PICKUP:       4,
  DELIVERED:              5,
  CANCELLED:              5,
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
  return status === 'PENDING_PAYMENT' || status === 'RECEIVED'
}

/** Métodos de pago que cobran vía MercadoPago: el importe ya viajó al gateway. */
const GATEWAY_PAYMENT_METHODS = ['MERCADOPAGO', 'QR_CODE']
/** Estados de un pago de gateway que bloquean el descuento: cobrado o en vuelo. */
const DISCOUNT_BLOCKING_PAYMENT_STATUSES = ['APPROVED', 'PENDING']

/**
 * Ventana del descuento: el pedido activo, después de RECEIVED y antes de
 * DELIVERED. Espeja ACTIVE_ORDER_STATUSES del backend.
 */
const DISCOUNTABLE_STATUSES = [
  'RECEIVED',
  'IN_PREPARATION',
  'ON_THE_WAY',
  'READY_FOR_PICKUP',
]

/**
 * ¿Se le puede aplicar un descuento manual a este pedido? (US-19-02)
 *
 * Espeja los guards del backend (`OrderService.applyDiscount`) para no ofrecerle
 * al operador un botón que va a terminar en 422:
 *  - solo MANAGER y ADMIN (el backend lo valida con @PreAuthorize; acá es UI)
 *  - nunca con un pago de gateway aprobado o pendiente
 *  - solo mientras el pedido está activo: en PENDING_PAYMENT todavía no está
 *    definido cómo se cobra, y desde DELIVERED el total ya se factura en el
 *    resumen del turno (un cancelado directamente no tiene nada que cobrar)
 *
 * Es una comprobación de conveniencia, no de seguridad: la autorización real es
 * del backend. Si los guards divergen, el modal muestra el 422 igual.
 */
export function canApplyDiscount(order, role) {
  if (role !== 'MANAGER' && role !== 'ADMIN') return false
  if (!order || !DISCOUNTABLE_STATUSES.includes(order.status)) return false
  return !(
    GATEWAY_PAYMENT_METHODS.includes(order.paymentMethod) &&
    DISCOUNT_BLOCKING_PAYMENT_STATUSES.includes(order.paymentStatus)
  )
}

/**
 * Motivos del descuento (US-19-02). El backend los persiste como enum en inglés
 * (`DiscountReason`); acá se mapean a la etiqueta que ve el operador. El `value`
 * es lo que viaja al endpoint — nunca la etiqueta.
 */
export const DISCOUNT_REASON_OPTIONS = [
  { value: 'CUSTOMER_PROMO', label: 'Promoción al cliente' },
  { value: 'TRANSFER_ADJUSTMENT', label: 'Ajuste por transferencia' },
  { value: 'OTHER', label: 'Otro' },
]

/**
 * Mensaje de error del descuento según el código de estado (US-19-02). El backend
 * distingue dos rechazos con causas y remedios distintos, y mezclarlos en un
 * "error genérico" deja al operador sin saber si corregir el formulario o dejar
 * de intentar:
 *  - 400: lo rechazó @Valid por el rango del porcentaje. El body trae
 *    "Validation failed", inútil para el operador, así que se reemplaza.
 *  - 422: lo rechazó un guard de negocio (pago de gateway detectado, o pedido con
 *    el pago pendiente). El mensaje del backend ya es específico y accionable, y
 *    distingue entre esos dos casos; se muestra tal cual.
 */
export function discountErrorMessage(err) {
  if (err?.status === 400) {
    return 'El porcentaje debe ser un número entre 0 y 100.'
  }
  if (err?.status === 422) {
    return (
      err.message ??
      'No se puede aplicar un descuento a este pedido: el cobro ya está tomado por MercadoPago o QR.'
    )
  }
  if (err?.message === 'network_error') {
    return 'Sin conexión. Verificá tu internet e intentá de nuevo.'
  }
  return 'No se pudo aplicar el descuento. Intentá de nuevo.'
}

export function canConfirmOrder({ cartItems, orderType, deliveryAddress }) {
  return cartItems.length > 0 &&
    (orderType !== 'DELIVERY' || (deliveryAddress ?? '').trim().length > 0)
}

/**
 * Número visible del pedido, secuencial y continuo por sucursal (US-16B-03):
 * "Orden #47". Es sólo presentación — el id interno del pedido sigue siendo el
 * UUID. Fallback al UUID truncado para pedidos legados sin orderNumber.
 */
export function formatOrderNumber(order) {
  const n = order?.orderNumber
  if (n != null) return `Orden #${n}`
  const id = String(order?.id ?? '')
  return id ? `#ORDER-${id.slice(0, 8)}` : ''
}

/**
 * Nombre a mostrar de un ítem del pedido (US-HH-04). Un ítem mitad y mitad se muestra como
 * "½ A + ½ B"; uno simple, con su nombre tal cual. Consume `productName` /
 * `secondProductName` de BackofficeOrderItemDTO.
 *
 * El formato es el mismo que usa el client en carrito, checkout y seguimiento
 * (client/src/utils/halfAndHalf.js). Es duplicación deliberada entre las dos apps: son
 * bundles Vite independientes, sin paquete compartido donde alojar el helper. Si el formato
 * cambia, hay que cambiarlo en los dos lados.
 */
export function orderItemDisplayName(item) {
  const name = item?.productName ?? ''
  const second = item?.secondProductName
  if (second) return `½ ${name} + ½ ${second}`
  // US-SIZE-F-03: el tamaño se muestra entre paréntesis, igual que en el client. Sólo
  // aparece con tamaño alternativo: el grande es implícito y no lleva sufijo.
  return item?.sizeName ? `${name} (${sizeLabel(item.sizeName)})` : name
}

/**
 * Etiqueta visible de un tamaño. El backend lo expone como enum (CHICA).
 */
export function sizeLabel(size) {
  if (size === 'CHICA') return 'Chica'
  if (size === 'GRANDE') return 'Grande'
  return size
}

/**
 * Precio unitario de un ítem mitad y mitad (US-HH-03): el mayor de los dos precios
 * efectivos de la sucursal. El menú ya expone `price` resuelto (priceOverride ?? price),
 * así que comparar esos valores da lo mismo que resuelve el backend al crear el pedido —
 * el precio mostrado antes de confirmar nunca difiere del cobrado.
 */
export function halfAndHalfUnitPrice(priceA, priceB) {
  return Math.max(Number(priceA ?? 0), Number(priceB ?? 0))
}

/**
 * Identidad de un ítem dentro del carrito del pedido manual. Un ítem simple se identifica
 * por su productId; uno combinado, por el par ordenado de ids — así elegir A+B o B+A cae en
 * la misma línea del carrito, y un combinado nunca se fusiona con el producto suelto.
 *
 * US-SIZE-F-03: un ítem con tamaño lleva su propia identidad para no fusionarse con el mismo
 * producto sin tamaño ("Grande", que es el comportamiento por defecto) ni con otro tamaño.
 * Tamaño y mitad y mitad son excluyentes, así que nunca coinciden en el mismo ítem.
 */
export function cartItemKey({ productId, secondProductId, productSizeId }) {
  if (secondProductId != null) {
    const [a, b] = [Number(productId), Number(secondProductId)].sort((x, y) => x - y)
    return `hh-${a}-${b}`
  }
  if (productSizeId != null) return `size-${productId}-${productSizeId}`
  return String(productId)
}
