import { createElement as h } from 'react'
import { formatOrderNumber } from '../utils/ordersUtils'

/**
 * Servicio de impresión y descarga de tickets de compra (US-16-01).
 *
 * Capa de abstracción única: ningún componente debe llamar a window.print() ni a
 * @react-pdf/renderer directamente — toda la lógica pasa por acá. Las firmas
 * printTicket(order, branch) y downloadTicket(order, branch) son estables: cuando
 * se implemente impresión térmica ESC/POS en el futuro solo cambia la
 * implementación interna, no la interfaz que consumen los componentes.
 *
 * Ambos métodos generan el mismo contenido a partir de buildTicketModel(): el
 * camino de impresión lo renderiza como HTML y el de descarga como PDF vía el
 * componente TicketDocument (US-16-02).
 *
 * @react-pdf/renderer y TicketDocument se cargan con import() dinámico dentro de
 * downloadTicket: la librería de PDF (pesada) queda fuera del bundle inicial y
 * sólo se baja cuando el operador descarga un ticket. printTicket (HTML) no la
 * necesita.
 */

const PAYMENT_METHOD_LABELS = {
  CASH: 'Efectivo',
  MERCADOPAGO: 'MercadoPago',
  QR_CODE: 'QR MercadoPago',
}

const ORDER_TYPE_LABELS = {
  DELIVERY: 'Delivery',
  TAKEAWAY: 'Retiro en local',
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatMoney(value) {
  return '$' + Number(value ?? 0).toLocaleString('es-AR', { maximumFractionDigits: 0 })
}

// Fecha legible del ticket: DD/MM/YYYY HH:MM.
function formatDateTime(value) {
  const d = value ? new Date(value) : new Date()
  if (Number.isNaN(d.getTime())) return ''
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()} `
    + `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

// Hora sola, para lectura rápida en cocina: HH:MM.
function formatTime(value) {
  const d = value ? new Date(value) : new Date()
  if (Number.isNaN(d.getTime())) return ''
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

// Fecha para el nombre de archivo: YYYY-MM-DD.
function fileDate(value) {
  const d = value ? new Date(value) : new Date()
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function shortId(id) {
  return String(id ?? '').slice(0, 8)
}

// Escapa texto para interpolar seguro dentro del HTML de la ventana de impresión.
function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

/**
 * Normaliza order + branch a un modelo plano y defensivo, única fuente de verdad
 * del contenido del ticket. Tanto el HTML de impresión como el PDF (vía
 * TicketDocument) lo consumen, garantizando que ambos rendericen exactamente lo
 * mismo. Exportada para que TicketDocument reutilice el mismo formateo.
 */
export function buildTicketModel(order, branch) {
  const o = order ?? {}
  const b = branch ?? {}
  const items = Array.isArray(o.items) ? o.items : []
  const sid = shortId(o.id)
  const isDelivery = o.orderType === 'DELIVERY'

  return {
    tenantName: b.tenantName ?? b.tenant?.name ?? '',
    branchName: b.name ?? o.branchName ?? '',
    branchAddress: b.address ?? '',
    orderNumber: formatOrderNumber(o),
    createdAt: formatDateTime(o.createdAt),
    customerName: o.customerName ?? '',
    customerPhone: o.customerPhone ?? '',
    orderTypeLabel: ORDER_TYPE_LABELS[o.orderType] ?? o.orderType ?? '',
    // La dirección es parte del comprobante sólo cuando el pedido es delivery;
    // en retiro en local no aplica y no se muestra.
    isDelivery,
    deliveryAddress: isDelivery ? (o.deliveryAddress ?? '') : '',
    items: items.map(it => ({
      quantity: it.quantity ?? 0,
      name: it.productName ?? '',
      unitPrice: formatMoney(it.unitPrice),
    })),
    total: formatMoney(o.totalAmount),
    paymentMethod: PAYMENT_METHOD_LABELS[o.paymentMethod] ?? o.paymentMethod ?? '',
    fileName: `ticket-${sid}-${fileDate(o.createdAt)}.pdf`,
  }
}

// ── Impresión no bloqueante (compartido por ticket y comanda) ──────────────
//
// El opener (la pestaña del backoffice) NO llama a print() ni close(). Abre la
// ventana hija con `noopener`, lo que fuerza a Chrome a ponerla en un proceso de
// renderizado separado: así el diálogo nativo de impresión —modal a nivel de
// proceso— no congela la pestaña del backoffice. (Verificado en Chrome real con
// el diálogo nativo: sin noopener el opener se congela; con noopener responde.)
//
// `noopener` hace que window.open() devuelva null, por eso el contenido no se
// puede escribir con document.write y viaja por un blob URL. Y como la ventana
// hija (documento blob:) hereda la CSP del backoffice (`script-src 'self'`, sin
// unsafe-inline), el disparo de print/close no puede ir inline: vive en el script
// externo same-origin /print-child.js (permitido por 'self'). Estas piezas son
// idénticas para el ticket y la comanda; se centralizan para que no diverjan.

// Botón de cierre manual: fallback por si onafterprint no dispara al cancelar
// (comportamiento inconsistente entre navegadores). Oculto en la impresión.
const CLOSE_BUTTON_CSS = `.close-btn { position: fixed; top: 12px; right: 12px; padding: 8px 14px;
      font: 600 13px Helvetica, Arial, sans-serif; color: #fff; background: #333;
      border: none; border-radius: 6px; cursor: pointer; }
    @media print { .no-print { display: none !important; } }`

const CLOSE_BUTTON_HTML = '<button type="button" id="ticket-close" class="close-btn no-print">Cerrar</button>'

// Referencia al bootstrap de impresión. URL absoluta del propio origen: en un
// documento blob: las rutas relativas resuelven contra el blob:, no contra el
// origen, así que hay que dar el origin explícito (que además es el que matchea
// `script-src 'self'`).
function printScriptTag() {
  return `<script src="${window.location.origin}/print-child.js"></script>`
}

// Abre la ventana hija con el HTML dado. noopener → proceso separado → el diálogo
// nativo no congela el opener. window.open devuelve null (esperado con noopener):
// no se puede distinguir "pop-up bloqueado", pero los handlers ya lo toleran.
function openPrintWindow(html) {
  const url = URL.createObjectURL(new Blob([html], { type: 'text/html' }))
  window.open(url, '_blank', 'noopener')
  // Revocar tras dar tiempo a que la hija cargue el blob (no hay handle para
  // saber cuándo cargó, por el noopener).
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

// ── Camino de impresión (HTML + window.print) ──────────────────────────────

function renderTicketHtml(model) {
  const rows = model.items.map(it => `
      <tr>
        <td class="qty">${escapeHtml(it.quantity)}</td>
        <td class="name">${escapeHtml(it.name)}</td>
        <td class="price">${escapeHtml(it.unitPrice)}</td>
      </tr>`).join('')

  // Fila etiqueta/valor del bloque de datos; se omite si el valor está vacío.
  const field = (label, value) => value
    ? `<div class="field"><span class="field-label">${escapeHtml(label)}</span>`
      + `<span class="field-value">${escapeHtml(value)}</span></div>`
    : ''

  // Bloque de datos: cliente, entrega y pago juntos (misma sección visual que el
  // PDF, sin separadores punteados intermedios). Cada fila se omite si está vacía.
  const dataBlock = [
    field('Cliente', model.customerName),
    field('Teléfono', model.customerPhone),
    field('Entrega', model.orderTypeLabel),
    model.isDelivery ? field('Dirección', model.deliveryAddress) : '',
    field('Pago', model.paymentMethod),
  ].join('')

  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(model.orderNumber)}</title>
  <style>
    @page { size: A4; margin: 16mm; }
    * { box-sizing: border-box; }
    body { font-family: Helvetica, Arial, sans-serif; color: #000; margin: 0; }
    .ticket { width: 100%; max-width: 340px; margin: 0 auto; }
    .center { text-align: center; }
    /* Encabezado: tenant destacado, sucursal y dirección */
    .tenant { font-size: 23px; font-weight: bold; letter-spacing: 0.5px; }
    .branch { font-size: 14px; font-weight: bold; margin-top: 3px; }
    .address { font-size: 11px; color: #666; margin-top: 1px; }
    /* Número de orden destacado + fecha */
    .order-number { font-size: 17px; font-weight: bold; margin-top: 12px; }
    .created-at { font-size: 11px; color: #666; margin-top: 2px; }
    .sep { border: none; border-top: 1px dashed #000; margin: 12px 0; }
    /* Bloque de datos etiqueta-valor */
    .field { display: flex; font-size: 13px; padding: 2px 0; }
    .field-label { color: #666; width: 84px; flex-shrink: 0; }
    .field-value { flex: 1; font-weight: bold; }
    /* Tabla de ítems */
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    thead th { font-size: 10px; color: #666; font-weight: normal; letter-spacing: 0.5px; text-align: left; padding-bottom: 4px; }
    td { padding: 2px 0; vertical-align: top; }
    th.qty, td.qty { width: 34px; }
    td.qty { font-weight: bold; }
    th.price, td.price { width: 88px; text-align: right; }
    /* Total: el elemento más destacado */
    .total { display: flex; justify-content: space-between; align-items: baseline; font-size: 26px; font-weight: bold; }
    .total .label { letter-spacing: 1px; }
    ${CLOSE_BUTTON_CSS}
  </style>
</head>
<body>
  ${CLOSE_BUTTON_HTML}
  <div class="ticket">
    <div class="center tenant">${escapeHtml(model.tenantName)}</div>
    <div class="center branch">${escapeHtml(model.branchName)}</div>
    ${model.branchAddress ? `<div class="center address">${escapeHtml(model.branchAddress)}</div>` : ''}
    <div class="center order-number">${escapeHtml(model.orderNumber)}</div>
    <div class="center created-at">${escapeHtml(model.createdAt)}</div>
    <hr class="sep" />
    ${dataBlock}
    <hr class="sep" />
    <table>
      <thead><tr><th class="qty">CANT</th><th class="name">DETALLE</th><th class="price">IMPORTE</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    <hr class="sep" />
    <div class="total"><span class="label">TOTAL</span><span>${escapeHtml(model.total)}</span></div>
  </div>
  ${printScriptTag()}
</body>
</html>`
}

// ── Camino de descarga (PDF @react-pdf/renderer) ───────────────────────────

function triggerDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

// ── API pública ────────────────────────────────────────────────────────────

/**
 * Imprime el ticket de un pedido. Abre una ventana nueva y le escribe el ticket
 * en HTML; esa ventana se encarga sola de disparar la impresión (window.onload)
 * y de cerrarse al terminar (window.onafterprint), más un botón "Cerrar" de
 * fallback.
 *
 * Clave: el opener (la pestaña del backoffice) NO llama a win.print() ni a
 * win.close(). Antes lo hacía de forma síncrona en su propio hilo, y como
 * window.print() bloquea hasta que se cierra el diálogo, la pestaña original
 * quedaba congelada. Ahora print/close corren en el hilo de la ventana hija y el
 * opener sigue respondiendo con normalidad.
 */
export function printTicket(order, branch) {
  openPrintWindow(renderTicketHtml(buildTicketModel(order, branch)))
}

// ── Camino de comanda (cocina) ─────────────────────────────────────────────
//
// La comanda es un documento distinto del ticket (US-16B-03): pensado para
// lectura rápida en cocina, no como comprobante de venta. Por eso tiene su
// propio modelo y render — mismo criterio que separa TicketDocument de
// ShiftSummaryDocument — reutilizando sólo los helpers de bajo nivel y el
// mecanismo de impresión no bloqueante. Contenido mínimo: número de orden
// grande, tipo de entrega (sin dirección), hora de recepción, ítems (cantidad +
// nombre, sin precios) y notas destacadas. Sin pago, sin totales.

/**
 * Normaliza un pedido al contenido mínimo de la comanda de cocina. No necesita
 * `branch`: la cocina imprime para su propia sucursal. Exportada para testear el
 * formateo sin pasar por el DOM.
 */
export function buildComandaModel(order) {
  const o = order ?? {}
  const items = Array.isArray(o.items) ? o.items : []
  return {
    orderNumber: formatOrderNumber(o),
    orderTypeLabel: ORDER_TYPE_LABELS[o.orderType] ?? o.orderType ?? '',
    receivedAt: formatTime(o.createdAt),
    items: items.map(it => ({
      quantity: it.quantity ?? 0,
      name: it.productName ?? '',
    })),
    notes: (o.notes ?? '').trim(),
  }
}

function renderComandaHtml(model) {
  const rows = model.items.map(it => `
      <div class="item">
        <span class="item-qty">${escapeHtml(it.quantity)}</span>
        <span class="item-name">${escapeHtml(it.name)}</span>
      </div>`).join('')

  // Notas: sólo si existen, en un recuadro destacado. print-color-adjust mantiene
  // el fondo al imprimir; el borde grueso asegura visibilidad aunque el navegador
  // descarte el color de fondo.
  const notesBlock = model.notes
    ? `<div class="notes">
        <div class="notes-label">NOTAS</div>
        <div class="notes-text">${escapeHtml(model.notes).replaceAll('\n', '<br>')}</div>
      </div>`
    : ''

  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>Comanda ${escapeHtml(model.orderNumber)}</title>
  <style>
    @page { size: A4; margin: 14mm; }
    * { box-sizing: border-box; }
    body { font-family: Helvetica, Arial, sans-serif; color: #000; margin: 0; }
    .comanda { width: 100%; max-width: 420px; margin: 0 auto; }
    /* Encabezado: número de orden enorme + tipo de entrega + hora */
    .order-number { font-size: 46px; font-weight: bold; letter-spacing: 0.5px; line-height: 1.05; }
    .order-type { font-size: 26px; font-weight: bold; text-transform: uppercase; margin-top: 4px; }
    .received { font-size: 15px; color: #444; margin-top: 4px; }
    .sep { border: none; border-top: 2px solid #000; margin: 14px 0; }
    /* Ítems: cantidad y nombre grandes, sin precios */
    .item { display: flex; align-items: baseline; padding: 6px 0; }
    .item-qty { font-size: 30px; font-weight: bold; width: 56px; flex-shrink: 0; }
    .item-name { font-size: 26px; }
    /* Notas destacadas */
    .notes { margin-top: 14px; border: 3px solid #000; border-radius: 8px; padding: 10px 12px;
      background: #ffe9a8; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .notes-label { font-size: 14px; font-weight: bold; letter-spacing: 1px; margin-bottom: 4px; }
    .notes-text { font-size: 20px; font-weight: bold; line-height: 1.3; }
    ${CLOSE_BUTTON_CSS}
  </style>
</head>
<body>
  ${CLOSE_BUTTON_HTML}
  <div class="comanda">
    <div class="order-number">${escapeHtml(model.orderNumber)}</div>
    <div class="order-type">${escapeHtml(model.orderTypeLabel)}</div>
    <div class="received">Recibido ${escapeHtml(model.receivedAt)}</div>
    <hr class="sep" />
    ${rows}
    ${notesBlock}
  </div>
  ${printScriptTag()}
</body>
</html>`
}

/**
 * Imprime la comanda de cocina de un pedido. Mismo mecanismo no bloqueante que
 * printTicket: el opener sólo abre la ventana y le escribe el HTML; el propio
 * documento hijo dispara la impresión y se cierra.
 */
export function printComanda(order) {
  openPrintWindow(renderComandaHtml(buildComandaModel(order)))
}

/**
 * Descarga el ticket de un pedido como PDF (mismo contenido que printTicket). El
 * layout vive en TicketDocument; acá solo se resuelve el nombre de archivo y se
 * dispara la descarga.
 *
 * pdf() y TicketDocument se importan dinámicamente para mantener
 * @react-pdf/renderer fuera del bundle inicial (se baja en el primer download).
 */
export async function downloadTicket(order, branch) {
  const { fileName } = buildTicketModel(order, branch)
  const [{ pdf }, { default: TicketDocument }] = await Promise.all([
    import('@react-pdf/renderer'),
    import('../components/TicketDocument'),
  ])
  const blob = await pdf(h(TicketDocument, { order, branch })).toBlob()
  triggerDownload(blob, fileName)
}

/**
 * Descarga el resumen de un turno como PDF (US-16-05). Mismo patrón que
 * downloadTicket: import() dinámico de pdf + ShiftSummaryDocument para no cargar
 * @react-pdf/renderer en el bundle inicial.
 *
 * `shift` es el objeto de estado del turno (openedAt, closedAt, summary y, si está
 * disponible, autoClose). Sirve tanto para un turno cerrado (Informe Z) como para
 * uno en curso (Informe X): en ese caso closedAt es null y el documento lo indica.
 * El id para el nombre de archivo sale del propio turno o de su summary; la fecha,
 * del cierre si existe, o de la apertura si el turno sigue abierto.
 */
export async function downloadShiftSummary(shift, branch) {
  const s = shift ?? {}
  const sid = shortId(s.shiftId ?? s.summary?.shiftId)
  const fileName = `resumen-turno-${sid}-${fileDate(s.closedAt ?? s.openedAt)}.pdf`
  const [{ pdf }, { default: ShiftSummaryDocument }] = await Promise.all([
    import('@react-pdf/renderer'),
    import('../components/ShiftSummaryDocument'),
  ])
  const blob = await pdf(h(ShiftSummaryDocument, { shift, branch })).toBlob()
  triggerDownload(blob, fileName)
}
