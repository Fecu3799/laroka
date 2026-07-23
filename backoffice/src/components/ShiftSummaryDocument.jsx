import { Document, Page, Text, View, StyleSheet } from "@react-pdf/renderer";
import {
  formatCurrency,
  formatShiftTime,
  formatDuration,
  closedByLabel,
  shiftDiscountSummary,
  shiftOrderDetailRows,
} from "../utils/shiftsUtils";
import { sharedPdfStyles as base } from "./pdfStyles";

/**
 * Resumen de turno en PDF A4, análogo al Informe Z de un POS (US-16-04, US-20-01).
 *
 * Sin estado propio. Recibe los mismos datos que ya consume Summary.jsx/useShift():
 * - shift: el objeto de estado del turno { openedAt, closedAt, openedBy, closedBy,
 *          autoClose?, summary }. summary es el CloseShiftResponseDTO (totalOrders,
 *          totalRevenue, cashRevenue, mpRevenue, qrRevenue, averageTicket, ...).
 * - branch: { name, tenantName } — nombres tomados del JWT (useAuth) / useBranch
 *          en el punto de invocación (US-16-05), igual que el `branch` del ticket.
 *
 * US-20-01: el encabezado suma contexto operativo (quién abrió y cerró, duración) y
 * el cuerpo gana jerarquía — el ticket promedio en Totales y el desglose por método
 * de pago encerrado en una caja para separarlo visualmente de los totales.
 *
 * "Cerrado por" refleja el auto-cierre: cuando el turno se cerró solo (sin staffUser
 * humano) muestra "Cierre automático" en vez de vacío — closedByLabel lo resuelve.
 */

const styles = StyleSheet.create({
  doc: { width: 320, marginHorizontal: "auto" },
  heading: { fontSize: 12, fontFamily: "Helvetica-Bold", marginBottom: 4 },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 2,
  },
  // Filas del desglose por motivo, sangradas bajo "Descuentos del turno".
  rowIndented: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 2,
    paddingLeft: 12,
  },
  label: { fontSize: 11 },
  value: { fontSize: 11, textAlign: "right" },
  totalLabel: { fontSize: 12, fontFamily: "Helvetica-Bold" },
  totalValue: {
    fontSize: 12,
    fontFamily: "Helvetica-Bold",
    textAlign: "right",
  },

  // Tabla de detalle de pedidos (US-20-03), a ancho completo del contenido A4.
  detailSection: { marginTop: 18 },
  tableHeadRow: {
    flexDirection: "row",
    borderBottomWidth: 1,
    borderBottomStyle: "solid",
    borderBottomColor: "#000",
    paddingBottom: 3,
    marginBottom: 2,
  },
  tableRow: {
    flexDirection: "row",
    paddingVertical: 2,
    borderBottomWidth: 0.5,
    borderBottomStyle: "solid",
    borderBottomColor: "#ccc",
  },
  th: { fontSize: 8, fontFamily: "Helvetica-Bold" },
  td: { fontSize: 8 },
  tdCancelled: { fontSize: 8, color: "#888" },
  // Columnas: n°, hora, origen, método, total(derecha), estado, descuento.
  // colTotal lleva paddingRight para que el importe no quede pegado a Estado.
  colNum: { width: 34 },
  colTime: { width: 44 },
  colOrigin: { width: 58 },
  colMethod: { width: 72 },
  colTotal: { width: 76, textAlign: "right", paddingRight: 30 },
  colStatus: { width: 80 },
  colDiscount: { width: 80 },
  tableFootRow: {
    flexDirection: "row",
    justifyContent: "flex-end",
    marginTop: 6,
    gap: 16,
  },
  footItem: { fontSize: 9, fontFamily: "Helvetica-Bold" },
});

export default function ShiftSummaryDocument({ shift, branch }) {
  const s = shift ?? {};
  const summary = s.summary ?? {};
  const b = branch ?? {};

  const openedAt = formatShiftTime(s.openedAt) ?? "—";
  const closedAt = s.closedAt ? formatShiftTime(s.closedAt) : "En curso";
  const duration = formatDuration(s.openedAt, s.closedAt);
  const openedBy = s.openedBy ?? "—";
  const closedBy = closedByLabel(s);
  const discounts = shiftDiscountSummary(summary);
  const detail = shiftOrderDetailRows(s.orderDetails);

  return (
    <Document>
      <Page size="A4" style={base.page}>
        <View style={styles.doc}>
          <Text style={base.title}>{b.tenantName ?? ""}</Text>
          <Text style={base.subtitle}>{b.name ?? ""}</Text>

          <View style={base.sep} />

          {/* Contexto operativo del turno */}
          <View style={styles.row}>
            <Text style={styles.label}>Apertura</Text>
            <Text style={styles.value}>{openedAt}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Cierre</Text>
            <Text style={styles.value}>{closedAt}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Duración</Text>
            <Text style={styles.value}>{duration}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Abierto por</Text>
            <Text style={styles.value}>{openedBy}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Cerrado por</Text>
            <Text style={styles.value}>{closedBy}</Text>
          </View>

          <View style={base.sep} />

          {/* Totales */}
          <Text style={styles.heading}>Totales</Text>
          <View style={styles.row}>
            <Text style={styles.label}>Cantidad de pedidos</Text>
            <Text style={styles.value}>{String(summary.totalOrders ?? 0)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>Ticket promedio</Text>
            <Text style={styles.value}>
              {formatCurrency(summary.averageTicket)}
            </Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.totalLabel}>Monto total vendido</Text>
            <Text style={styles.totalValue}>
              {formatCurrency(summary.totalRevenue)}
            </Text>
          </View>

          {/* Por método de pago — mismo tratamiento que las demás secciones (sin caja) */}
          <View style={base.sep} />
          <Text style={styles.heading}>Por método de pago</Text>
          <View style={styles.row}>
            <Text style={styles.label}>Efectivo</Text>
            <Text style={styles.value}>
              {formatCurrency(summary.cashRevenue)}
            </Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>MercadoPago</Text>
            <Text style={styles.value}>
              {formatCurrency(summary.mpRevenue)}
            </Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.label}>QR</Text>
            <Text style={styles.value}>
              {formatCurrency(summary.qrRevenue)}
            </Text>
          </View>

          {/* Descuentos del turno (US-20-02). Solo si hubo alguno: sin descuentos la
              sección se omite para no generar ruido visual en el informe. */}
          {discounts.hasDiscounts && (
            <>
              <View style={base.sep} />
              <Text style={styles.heading}>Descuentos del turno</Text>
              <View style={styles.row}>
                <Text style={styles.totalLabel}>Total descontado</Text>
                <Text style={styles.totalValue}>
                  {formatCurrency(discounts.total)}
                </Text>
              </View>
              <View style={styles.row}>
                <Text style={styles.label}>Pedidos con descuento</Text>
                <Text style={styles.value}>{String(discounts.count)}</Text>
              </View>
              {discounts.breakdown.map((d) => (
                <View style={styles.rowIndented} key={d.label}>
                  <Text style={styles.label}>{d.label}</Text>
                  <Text style={styles.value}>{formatCurrency(d.amount)}</Text>
                </View>
              ))}
            </>
          )}
        </View>

        {/* Detalle de pedidos del turno (US-20-03). A ancho completo, fuera del
            bloque angosto del resumen. Cada fila lleva wrap=false para que un pedido
            no se parta entre páginas; react-pdf hace fluir las filas a la página
            siguiente cuando la tabla es larga. */}
        {detail.hasOrders && (
          <View style={styles.detailSection}>
            <Text style={styles.heading}>Detalle de pedidos</Text>
            <View style={styles.tableHeadRow}>
              <Text style={[styles.th, styles.colNum]}>N°</Text>
              <Text style={[styles.th, styles.colTime]}>Hora</Text>
              <Text style={[styles.th, styles.colOrigin]}>Origen</Text>
              <Text style={[styles.th, styles.colMethod]}>Método</Text>
              <Text style={[styles.th, styles.colTotal]}>Total</Text>
              <Text style={[styles.th, styles.colStatus]}>Estado</Text>
              <Text style={[styles.th, styles.colDiscount]}>Descuento</Text>
            </View>
            {detail.rows.map((r, i) => {
              const cell = r.cancelled ? styles.tdCancelled : styles.td;
              return (
                <View style={styles.tableRow} key={i} wrap={false}>
                  <Text style={[cell, styles.colNum]}>{r.orderNumber}</Text>
                  <Text style={[cell, styles.colTime]}>{r.time}</Text>
                  <Text style={[cell, styles.colOrigin]}>{r.origin}</Text>
                  <Text style={[cell, styles.colMethod]}>{r.method}</Text>
                  <Text style={[cell, styles.colTotal]}>{r.total}</Text>
                  <Text style={[cell, styles.colStatus]}>{r.status}</Text>
                  <Text style={[cell, styles.colDiscount]}>{r.discount}</Text>
                </View>
              );
            })}

            {/* Subtotal por método al pie, para el arqueo (US-20-03). Suma sólo
                entregados; cuadra con "Por método de pago" de US-20-01. */}
            <View style={styles.tableFootRow}>
              {detail.methodSubtotals.map((m) => (
                <Text style={styles.footItem} key={m.label}>
                  {`${m.label}: ${formatCurrency(m.amount)}`}
                </Text>
              ))}
            </View>
          </View>
        )}
      </Page>
    </Document>
  );
}
