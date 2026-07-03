import { StyleSheet } from '@react-pdf/renderer'

/**
 * Estilos base compartidos por los documentos PDF (US-16-04).
 *
 * La tipografía (Helvetica), la página A4 con márgenes y el separador punteado
 * son comunes a TicketDocument y ShiftSummaryDocument. Se centralizan acá para
 * que ambos mantengan el mismo look & feel sin duplicar los tokens.
 */
export const sharedPdfStyles = StyleSheet.create({
  page: { fontFamily: 'Helvetica', fontSize: 11, paddingVertical: 40, paddingHorizontal: 48 },
  title: { fontSize: 15, fontFamily: 'Helvetica-Bold', textAlign: 'center' },
  subtitle: { fontSize: 11, textAlign: 'center' },
  sep: { borderBottomWidth: 1, borderBottomStyle: 'dashed', borderBottomColor: '#000', marginVertical: 8 },
})
