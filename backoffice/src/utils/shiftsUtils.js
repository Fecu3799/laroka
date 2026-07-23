// Helpers compartidos por las vistas de turno (RESUMEN y, en US-08-F-04,
// el modal de historial). formatDuration se exporta desde acá justamente
// para que ambas vistas usen la misma fuente de verdad.
import { DISCOUNT_REASON_LABEL } from './ordersUtils'

/**
 * Duración legible entre apertura y cierre (o "ahora" para un turno en curso).
 * @param {string|Date|null} openedAt
 * @param {string|Date|null} closedAtOrNow  cierre, o now() si el turno sigue abierto
 * @returns {string} ej: "3h 42m", "47m"
 */
export function formatDuration(openedAt, closedAtOrNow) {
  if (!openedAt) return '—'
  const start = new Date(openedAt)
  const end = closedAtOrNow ? new Date(closedAtOrNow) : new Date()
  const totalMinutes = Math.max(0, Math.floor((end - start) / 60000))
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes}m`
  return `${hours}h ${minutes}m`
}

/**
 * Etiqueta de "Cerrado por" para el PDF de resumen (US-20-01).
 *  - turno auto-cerrado (por duración máxima, sin cierre manual): "Cierre automático"
 *    — no hay staffUser humano, autoCloseShift deja closedBy null en el backend.
 *  - turno cerrado a mano: el nombre de quien lo cerró.
 *  - turno en curso (sin cierre todavía): "En curso".
 * El orden importa: autoClose se evalúa primero porque un auto-cierre también tiene
 * closedBy null, y no debe caer en "En curso".
 */
export function closedByLabel(shift) {
  const s = shift ?? {}
  if (s.autoClose) return 'Cierre automático'
  if (s.closedBy) return s.closedBy
  return 'En curso'
}

/**
 * Agregado de "Descuentos del turno" para el PDF de resumen (US-20-02). A partir del
 * summary del backend arma el total descontado, la cantidad de pedidos con descuento
 * vigente y el desglose por motivo (solo los motivos con monto > 0, con su etiqueta en
 * español desde DISCOUNT_REASON_LABEL —única fuente de verdad, compartida con el modal).
 *
 * hasDiscounts es false cuando el turno no tuvo ningún descuento aplicado; el documento
 * usa ese flag para ocultar la sección y no generar ruido visual.
 */
export function shiftDiscountSummary(summary) {
  const s = summary ?? {}
  const count = Number(s.discountedOrders ?? 0)
  const total = Number(s.totalDiscount ?? 0)
  const byReason = {
    CUSTOMER_PROMO: Number(s.discountCustomerPromo ?? 0),
    TRANSFER_ADJUSTMENT: Number(s.discountTransferAdjustment ?? 0),
    OTHER: Number(s.discountOther ?? 0),
  }
  const breakdown = Object.entries(byReason)
    .filter(([, amount]) => amount > 0)
    .map(([reason, amount]) => ({ label: DISCOUNT_REASON_LABEL[reason] ?? reason, amount }))
  return { hasDiscounts: count > 0, total, count, breakdown }
}

const ORIGIN_LABEL = { CLIENT: 'App', BACKOFFICE: 'Mostrador' }
const ORDER_STATUS_LABEL = { DELIVERED: 'Entregado', CANCELLED: 'Cancelado' }
// Etiquetas de método para el PDF. Local a este util: la del backoffice (Orders.jsx)
// está incompleta (sin QR) y no se exporta.
const PAYMENT_METHOD_LABEL = { CASH: 'Efectivo', MERCADOPAGO: 'MercadoPago', QR_CODE: 'QR' }

/**
 * Filas del detalle de pedidos del turno para el PDF (US-20-03) y el subtotal por
 * método de pago al pie. `orderDetails` es la lista que devuelve el backend
 * (getShiftOrderDetails): una fila por pedido terminal, ya ordenada por hora.
 *
 * Cada fila trae textos listos para render (hora, origen, método, total, estado y el
 * motivo del descuento si lo tuvo). El subtotal por método suma sólo los DELIVERED —
 * un pedido cancelado no ingresó dinero—, así el pie cuadra con "Por método de pago"
 * de US-20-01. Devuelve hasOrders=false si el turno no tuvo pedidos terminales.
 */
export function shiftOrderDetailRows(orderDetails) {
  const list = Array.isArray(orderDetails) ? orderDetails : []
  const rows = list.map(o => ({
    time: formatShiftClock(o.createdAt),
    orderNumber: o.orderNumber != null ? `#${o.orderNumber}` : '—',
    origin: ORIGIN_LABEL[o.origin] ?? o.origin ?? '—',
    method: PAYMENT_METHOD_LABEL[o.paymentMethod] ?? o.paymentMethod ?? '—',
    total: formatCurrency(o.totalAmount),
    status: ORDER_STATUS_LABEL[o.status] ?? o.status ?? '—',
    cancelled: o.status === 'CANCELLED',
    discount: o.discountReason ? (DISCOUNT_REASON_LABEL[o.discountReason] ?? o.discountReason) : '—',
  }))

  const subtotals = { CASH: 0, MERCADOPAGO: 0, QR_CODE: 0 }
  for (const o of list) {
    if (o.status !== 'DELIVERED') continue
    if (o.paymentMethod in subtotals) subtotals[o.paymentMethod] += Number(o.totalAmount ?? 0)
  }
  const methodSubtotals = [
    { label: 'Efectivo', amount: subtotals.CASH },
    { label: 'MercadoPago', amount: subtotals.MERCADOPAGO },
    { label: 'QR', amount: subtotals.QR_CODE },
  ]

  return { hasOrders: rows.length > 0, rows, methodSubtotals }
}

/**
 * Moneda ARS sin decimales. Devuelve "—" si el valor es nulo.
 */
export function formatCurrency(amount) {
  if (amount == null) return '—'
  return new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: 'ARS',
    maximumFractionDigits: 0,
  }).format(amount)
}

/**
 * Hora de apertura/cierre en formato corto (dd/mm HH:mm).
 */
export function formatShiftTime(value) {
  if (!value) return null
  return new Date(value).toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/**
 * Solo la hora (HH:mm) — usado en el rango de período del card superior.
 */
export function formatShiftClock(value) {
  if (!value) return '—'
  return new Date(value).toLocaleTimeString('es-AR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/**
 * Fecha corta con día de semana (ej: "Mar 10 jun") — subtítulo del encargado.
 */
export function formatShiftDate(value) {
  if (!value) return ''
  const txt = new Date(value).toLocaleDateString('es-AR', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
  }).replace(/\.,?/g, '').replace(/,/g, '')
  return txt.charAt(0).toUpperCase() + txt.slice(1)
}

/**
 * Timestamp compacto de apertura para identificar un turno: DDMMYY-HH:mm
 * (formato LATAM, 24h). Ej: "160726-20:55". Reemplaza al sufijo del UUID como
 * etiqueta legible del turno.
 */
export function formatShiftStamp(value) {
  if (!value) return '—'
  const d = new Date(value)
  const p = n => String(n).padStart(2, '0')
  const date = `${p(d.getDate())}${p(d.getMonth() + 1)}${p(d.getFullYear() % 100)}`
  const time = `${p(d.getHours())}:${p(d.getMinutes())}`
  return `${date}-${time}`
}

/**
 * Iniciales para el avatar del encargado (máx. 2 letras).
 */
export function getInitials(name) {
  if (!name) return '?'
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(w => w[0])
    .join('')
    .toUpperCase()
}

/**
 * Porcentaje (0-100) de una parte respecto al total, redondeado.
 * Devuelve 0 si total es 0 para evitar NaN/división por cero.
 */
export function percentOf(part, total) {
  if (!total || total <= 0) return 0
  return Math.round((Number(part) / Number(total)) * 100)
}
