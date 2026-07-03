import { Document, Page, Text, View, StyleSheet } from '@react-pdf/renderer'
import { buildTicketModel } from '../services/ticketService'

/**
 * Layout definitivo del ticket de compra en PDF (US-16-02).
 *
 * Recibe { order, branch } y no tiene estado propio: delega el formateo en
 * buildTicketModel (única fuente de verdad, compartida con el camino de impresión
 * HTML del servicio) para no duplicar esa lógica acá. Solo describe la
 * presentación con primitivas de @react-pdf/renderer.
 *
 * Fuente Helvetica / Helvetica-Bold: incluidas en @react-pdf/renderer, sin carga
 * externa. Página A4.
 */

const styles = StyleSheet.create({
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

export default function TicketDocument({ order, branch }) {
  const model = buildTicketModel(order, branch)

  return (
    <Document>
      <Page size="A4" style={styles.page}>
        <View style={styles.ticket}>
          <Text style={styles.tenant}>{model.tenantName}</Text>
          <Text style={styles.branch}>{model.branchName}</Text>
          <Text style={styles.branch}>{model.branchAddress}</Text>

          <View style={styles.sep} />

          <Text style={styles.meta}>{model.orderNumber}</Text>
          <Text style={styles.meta}>{model.createdAt}</Text>

          <View style={styles.sep} />

          {model.items.map((it, i) => (
            <View style={styles.row} key={i}>
              <Text style={styles.qty}>{String(it.quantity)}</Text>
              <Text style={styles.name}>{it.name}</Text>
              <Text style={styles.price}>{it.unitPrice}</Text>
            </View>
          ))}

          <View style={styles.sep} />

          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>TOTAL</Text>
            <Text style={styles.totalLabel}>{model.total}</Text>
          </View>
          <Text style={styles.method}>{model.paymentMethod}</Text>
        </View>
      </Page>
    </Document>
  )
}
