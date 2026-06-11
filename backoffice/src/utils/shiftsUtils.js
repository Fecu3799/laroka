// Helpers compartidos por las vistas de turno (RESUMEN y, en US-08-F-04,
// el modal de historial). formatDuration se exporta desde acá justamente
// para que ambas vistas usen la misma fuente de verdad.

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
