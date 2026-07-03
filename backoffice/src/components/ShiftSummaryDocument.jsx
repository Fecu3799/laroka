import { Document, Page, Text, View, StyleSheet } from '@react-pdf/renderer'
import { formatCurrency, formatShiftTime } from '../utils/shiftsUtils'
import { sharedPdfStyles as base } from './pdfStyles'

/**
 * Resumen de turno en PDF, análogo al Informe Z de un POS (US-16-04).
 *
 * Sin estado propio. Recibe los mismos datos que ya consume Summary.jsx/useShift():
 * - shift: el objeto de estado del turno { openedAt, closedAt, summary, autoClose? }.
 *          summary es el CloseShiftResponseDTO (totalOrders, totalRevenue,
 *          cashRevenue, mpRevenue, qrRevenue, ...).
 * - branch: { name, tenantName } — nombres tomados del JWT (useAuth) / useBranch
 *          en el punto de invocación (US-16-05), igual que el `branch` del ticket.
 *
 * Reutiliza los formateadores de shiftsUtils (los mismos que la pantalla de
 * Resumen) y el StyleSheet base compartido con TicketDocument. Página A4,
 * Helvetica.
 *
 * Nota (US-16-04): `autoClose` no viaja hoy en el estado de useShift() para
 * turnos cerrados/historial; se lee de shift.autoClose y la línea "cerrado
 * automáticamente" sólo aparece si llega en true. Ver reporte de la US.
 */

const styles = StyleSheet.create({
  doc: { width: 320, marginHorizontal: 'auto' },
  heading: { fontSize: 12, fontFamily: 'Helvetica-Bold', marginBottom: 3 },
  headingSpaced: { fontSize: 12, fontFamily: 'Helvetica-Bold', marginTop: 8, marginBottom: 3 },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 2 },
  label: { fontSize: 11 },
  value: { fontSize: 11, textAlign: 'right' },
  totalLabel: { fontSize: 12, fontFamily: 'Helvetica-Bold' },
  totalValue: { fontSize: 12, fontFamily: 'Helvetica-Bold', textAlign: 'right' },
  autoClose: { fontSize: 11, fontFamily: 'Helvetica-Bold', textAlign: 'center', marginTop: 4 },
})

export default function ShiftSummaryDocument({ shift, branch }) {
  const s = shift ?? {}
  const summary = s.summary ?? {}
  const b = branch ?? {}

  const openedAt = formatShiftTime(s.openedAt) ?? '—'
  const closedAt = s.closedAt ? formatShiftTime(s.closedAt) : 'En curso'
  const autoClosed = s.autoClose === true

  return (
    <Document>
      <Page size="A4" style={base.page}>
        <View style={styles.doc}>
          <Text style={base.title}>{b.tenantName ?? ''}</Text>
          <Text style={base.subtitle}>{b.name ?? ''}</Text>

          <View style={styles.row}>
            <Text style={styles.label}>Apertura</Text>
            <Text style={styles.value}>{openedAt}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Cierre</Text>
            <Text style={styles.value}>{closedAt}</Text>
          </View>

          <View style={base.sep} />

          <Text style={styles.heading}>Totales</Text>
          <View style={styles.row}>
            <Text style={styles.label}>Cantidad de pedidos</Text>
            <Text style={styles.value}>{String(summary.totalOrders ?? 0)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.totalLabel}>Monto total vendido</Text>
            <Text style={styles.totalValue}>{formatCurrency(summary.totalRevenue)}</Text>
          </View>

          <Text style={styles.headingSpaced}>Por método de pago</Text>
          <View style={styles.row}>
            <Text style={styles.label}>Efectivo</Text>
            <Text style={styles.value}>{formatCurrency(summary.cashRevenue)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>MercadoPago</Text>
            <Text style={styles.value}>{formatCurrency(summary.mpRevenue)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>QR</Text>
            <Text style={styles.value}>{formatCurrency(summary.qrRevenue)}</Text>
          </View>

          <View style={base.sep} />

          {autoClosed && (
            <Text style={styles.autoClose}>Turno cerrado automáticamente</Text>
          )}
        </View>
      </Page>
    </Document>
  )
}
