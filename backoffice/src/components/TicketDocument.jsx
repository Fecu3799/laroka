import { Document, Page, Text, View, StyleSheet } from '@react-pdf/renderer'
import { buildTicketModel } from '../services/ticketService'
import { sharedPdfStyles as base } from './pdfStyles'

/**
 * Layout definitivo del ticket de compra en PDF (US-16-02, rediseño US-16B-02).
 *
 * Recibe { order, branch } y no tiene estado propio: delega el formateo en
 * buildTicketModel (única fuente de verdad, compartida con el camino de impresión
 * HTML del servicio) para no duplicar esa lógica acá. Solo describe la
 * presentación con primitivas de @react-pdf/renderer.
 *
 * Página A4 y separador punteado vienen de sharedPdfStyles; la tipografía del
 * ticket (jerarquía tenant / n° de orden / total) es específica de este documento
 * y vive en el StyleSheet local para no alterar ShiftSummaryDocument, que comparte
 * base.title/subtitle. Fuente Helvetica / Helvetica-Bold: incluidas en
 * @react-pdf/renderer, sin carga externa.
 *
 * Jerarquía visual (US-16B-02): el TOTAL es el elemento más grande, seguido por el
 * nombre del tenant (identidad del comprobante) y el número de orden. Los
 * separadores punteados quedan sólo en las transiciones de sección importantes:
 * encabezado → datos, datos → ítems, ítems → total.
 */

const MUTED = '#666'

const styles = StyleSheet.create({
  ticket: { width: 280, marginHorizontal: 'auto' },

  // Encabezado centrado
  tenant: { fontSize: 18, fontFamily: 'Helvetica-Bold', textAlign: 'center', letterSpacing: 0.5 },
  branch: { fontSize: 11, fontFamily: 'Helvetica-Bold', textAlign: 'center', marginTop: 3 },
  address: { fontSize: 9, textAlign: 'center', color: MUTED, marginTop: 1 },

  // Número de orden (destacado) + fecha
  orderNumber: { fontSize: 13, fontFamily: 'Helvetica-Bold', textAlign: 'center', marginTop: 10 },
  createdAt: { fontSize: 9, textAlign: 'center', color: MUTED, marginTop: 2 },

  // Bloque de datos etiqueta-valor
  fieldRow: { flexDirection: 'row', paddingVertical: 2 },
  fieldLabel: { fontSize: 10, color: MUTED, width: 64 },
  fieldValue: { fontSize: 10, flex: 1, fontFamily: 'Helvetica-Bold' },

  // Tabla de ítems — layout de columnas (compartido entre encabezado y filas)
  qtyCol: { width: 26 },
  nameCol: { flex: 1 },
  priceCol: { width: 70, textAlign: 'right' },
  itemsHead: { flexDirection: 'row', marginBottom: 4 },
  itemsHeadText: { fontSize: 8, color: MUTED, letterSpacing: 0.5 },
  row: { flexDirection: 'row', paddingVertical: 2 },
  qtyText: { fontSize: 10, fontFamily: 'Helvetica-Bold' },
  itemText: { fontSize: 10 },

  // Total (elemento más destacado del documento: la fuente más grande)
  totalRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  totalLabel: { fontSize: 21, fontFamily: 'Helvetica-Bold', letterSpacing: 1 },
  totalValue: { fontSize: 21, fontFamily: 'Helvetica-Bold' },
})

// Fila etiqueta/valor del bloque de datos; no renderiza nada si el valor está vacío.
function Field({ label, value }) {
  if (!value) return null
  return (
    <View style={styles.fieldRow}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Text style={styles.fieldValue}>{value}</Text>
    </View>
  )
}

export default function TicketDocument({ order, branch }) {
  const model = buildTicketModel(order, branch)

  return (
    <Document>
      <Page size="A4" style={base.page}>
        <View style={styles.ticket}>
          {/* Encabezado: tenant destacado, sucursal y dirección */}
          <Text style={styles.tenant}>{model.tenantName}</Text>
          <Text style={styles.branch}>{model.branchName}</Text>
          {!!model.branchAddress && <Text style={styles.address}>{model.branchAddress}</Text>}

          {/* Número de orden destacado + fecha/hora */}
          <Text style={styles.orderNumber}>{model.orderNumber}</Text>
          <Text style={styles.createdAt}>{model.createdAt}</Text>

          <View style={base.sep} />

          {/* Datos de cliente / entrega / pago */}
          <Field label="Cliente" value={model.customerName} />
          <Field label="Teléfono" value={model.customerPhone} />
          <Field label="Entrega" value={model.orderTypeLabel} />
          {model.isDelivery && <Field label="Dirección" value={model.deliveryAddress} />}
          <Field label="Pago" value={model.paymentMethod} />

          <View style={base.sep} />

          {/* Detalle de ítems: cantidad a la izquierda, importe a la derecha */}
          <View style={styles.itemsHead}>
            <Text style={[styles.qtyCol, styles.itemsHeadText]}>CANT</Text>
            <Text style={[styles.nameCol, styles.itemsHeadText]}>DETALLE</Text>
            <Text style={[styles.priceCol, styles.itemsHeadText]}>IMPORTE</Text>
          </View>
          {model.items.map((it, i) => (
            <View style={styles.row} key={i}>
              <Text style={[styles.qtyCol, styles.qtyText]}>{String(it.quantity)}</Text>
              <Text style={[styles.nameCol, styles.itemText]}>{it.name}</Text>
              <Text style={[styles.priceCol, styles.itemText]}>{it.unitPrice}</Text>
            </View>
          ))}

          <View style={base.sep} />

          {/* Total: el elemento más destacado del comprobante */}
          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>TOTAL</Text>
            <Text style={styles.totalValue}>{model.total}</Text>
          </View>
        </View>
      </Page>
    </Document>
  )
}
