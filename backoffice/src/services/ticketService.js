import { createElement as h } from 'react'
import { Document, Page, Text, View, StyleSheet, pdf } from '@react-pdf/renderer'

/**
 * Servicio de impresión y descarga de tickets de compra (US-16-01).
 *
 * Capa de abstracción única: ningún componente debe llamar a window.print() ni a
 * @react-pdf/renderer directamente — toda la lógica pasa por acá. Las firmas
 * printTicket(order, branch) y downloadTicket(order, branch) son estables: cuando
 * se implemente impresión térmica ESC/POS en el futuro solo cambia la
 * implementación interna, no la interfaz que consumen los componentes.
 *
 * Hoy ambos métodos generan el mismo contenido a partir de buildTicketModel(); el
 * layout definitivo del documento se aborda en US-16-02.
 */

const PAYMENT_METHOD_LABELS = {
  CASH: 'Efectivo',
  MERCADOPAGO: 'MercadoPago',
  QR_CODE: 'QR MercadoPago',
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
 * del contenido del ticket. Tanto el HTML de impresión como el PDF lo consumen,
 * garantizando que ambos rendericen exactamente lo mismo.
 */
function buildTicketModel(order, branch) {
  const o = order ?? {}
  const b = branch ?? {}
  const items = Array.isArray(o.items) ? o.items : []
  const sid = shortId(o.id)

  return {
    tenantName: b.tenantName ?? b.tenant?.name ?? '',
    branchName: b.name ?? o.branchName ?? '',
    branchAddress: b.address ?? '',
    orderNumber: `#ORDER-${sid}`,
    createdAt: formatDateTime(o.createdAt),
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

// ── Camino de impresión (HTML + window.print) ──────────────────────────────

function renderTicketHtml(model) {
  const rows = model.items.map(it => `
      <tr>
        <td class="qty">${escapeHtml(it.quantity)}</td>
        <td class="name">${escapeHtml(it.name)}</td>
        <td class="price">${escapeHtml(it.unitPrice)}</td>
      </tr>`).join('')

  return `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(model.orderNumber)}</title>
  <style>
    @page { size: A4; margin: 16mm; }
    * { box-sizing: border-box; }
    body { font-family: Helvetica, Arial, sans-serif; color: #000; margin: 0; }
    .ticket { width: 100%; max-width: 320px; margin: 0 auto; }
    .center { text-align: center; }
    .tenant { font-size: 16px; font-weight: bold; }
    .branch { font-size: 12px; }
    .sep { border: none; border-top: 1px dashed #000; margin: 8px 0; }
    .meta { font-size: 12px; }
    table { width: 100%; border-collapse: collapse; font-size: 12px; }
    td { padding: 2px 0; vertical-align: top; }
    td.qty { width: 28px; }
    td.price { width: 72px; text-align: right; }
    .total { display: flex; justify-content: space-between; font-size: 13px; font-weight: bold; }
    .method { font-size: 12px; text-align: right; }
  </style>
</head>
<body>
  <div class="ticket">
    <div class="center tenant">${escapeHtml(model.tenantName)}</div>
    <div class="center branch">${escapeHtml(model.branchName)}</div>
    <div class="center branch">${escapeHtml(model.branchAddress)}</div>
    <hr class="sep" />
    <div class="meta">${escapeHtml(model.orderNumber)}</div>
    <div class="meta">${escapeHtml(model.createdAt)}</div>
    <hr class="sep" />
    <table><tbody>${rows}</tbody></table>
    <hr class="sep" />
    <div class="total"><span>TOTAL</span><span>${escapeHtml(model.total)}</span></div>
    <div class="method">${escapeHtml(model.paymentMethod)}</div>
  </div>
</body>
</html>`
}

// ── Camino de descarga (PDF @react-pdf/renderer) ───────────────────────────

const pdfStyles = StyleSheet.create({
  page: { fontFamily: 'Helvetica', fontSize: 11, paddingVertical: 40, paddingHorizontal: 48 },
  ticket: { width: 260, marginHorizontal: 'auto' },
  tenant: { fontSize: 15, fontFamily: 'Helvetica-Bold', textAlign: 'center' },
  branch: { fontSize: 11, textAlign: 'center' },
  sep: { borderBottomWidth: 1, borderBottomStyle: 'dashed', borderBottomColor: '#000', marginVertical: 8 },
  meta: { fontSize: 11 },
  row: { flexDirection: 'row', paddingVertical: 1 },
  qty: { width: 24 },
  name: { flex: 1 },
  price: { width: 64, textAlign: 'right' },
  totalRow: { flexDirection: 'row', justifyContent: 'space-between' },
  totalLabel: { fontSize: 12, fontFamily: 'Helvetica-Bold' },
  method: { fontSize: 11, textAlign: 'right' },
})

function buildTicketPdfElement(model) {
  return h(Document, null,
    h(Page, { size: 'A4', style: pdfStyles.page },
      h(View, { style: pdfStyles.ticket },
        h(Text, { style: pdfStyles.tenant }, model.tenantName),
        h(Text, { style: pdfStyles.branch }, model.branchName),
        h(Text, { style: pdfStyles.branch }, model.branchAddress),
        h(View, { style: pdfStyles.sep }),
        h(Text, { style: pdfStyles.meta }, model.orderNumber),
        h(Text, { style: pdfStyles.meta }, model.createdAt),
        h(View, { style: pdfStyles.sep }),
        ...model.items.map((it, i) =>
          h(View, { style: pdfStyles.row, key: i },
            h(Text, { style: pdfStyles.qty }, String(it.quantity)),
            h(Text, { style: pdfStyles.name }, it.name),
            h(Text, { style: pdfStyles.price }, it.unitPrice),
          )),
        h(View, { style: pdfStyles.sep }),
        h(View, { style: pdfStyles.totalRow },
          h(Text, { style: pdfStyles.totalLabel }, 'TOTAL'),
          h(Text, { style: pdfStyles.totalLabel }, model.total),
        ),
        h(Text, { style: pdfStyles.method }, model.paymentMethod),
      ),
    ),
  )
}

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
 * Imprime el ticket de un pedido. Abre una ventana nueva, renderiza el ticket en
 * HTML y dispara window.print() antes de cerrarla.
 */
export function printTicket(order, branch) {
  const model = buildTicketModel(order, branch)
  const win = window.open('', '_blank')
  if (!win) {
    throw new Error('No se pudo abrir la ventana de impresión. Habilitá los pop-ups.')
  }
  win.document.open()
  win.document.write(renderTicketHtml(model))
  win.document.close()
  win.focus()
  win.print()
  win.close()
}

/**
 * Descarga el ticket de un pedido como PDF (mismo contenido que printTicket).
 */
export async function downloadTicket(order, branch) {
  const model = buildTicketModel(order, branch)
  const blob = await pdf(buildTicketPdfElement(model)).toBlob()
  triggerDownload(blob, model.fileName)
}
