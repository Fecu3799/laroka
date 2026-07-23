import { describe, test, expect } from 'vitest'
import ShiftSummaryDocument from '../components/ShiftSummaryDocument'
import {
  closedByLabel,
  formatDuration,
  shiftDiscountSummary,
  shiftOrderDetailRows,
} from '../utils/shiftsUtils'

// ShiftSummaryDocument es un componente puro que devuelve un árbol de primitivas de
// @react-pdf/renderer (no se renderiza a DOM). Se lo invoca como función y se recorren
// los hijos de tipo string para verificar qué textos quedan en el PDF, sin necesitar
// un render real de react-pdf. Es el análogo al buildTicketModel del ticket.
function collectText(node, acc = []) {
  if (node == null || node === false) return acc
  if (typeof node === 'string' || typeof node === 'number') {
    acc.push(String(node))
    return acc
  }
  if (Array.isArray(node)) {
    node.forEach(n => collectText(n, acc))
    return acc
  }
  if (node.props && node.props.children != null) {
    collectText(node.props.children, acc)
  }
  return acc
}

function renderTexts(shift, branch) {
  return collectText(ShiftSummaryDocument({ shift, branch }))
}

const BRANCH = { tenantName: 'La Roka', name: 'Playa Unión' }

const BASE_SUMMARY = {
  totalOrders: 10,
  totalRevenue: 110000,
  averageTicket: 11000,
  cashRevenue: 50000,
  mpRevenue: 40000,
  qrRevenue: 20000,
  // Sin descuentos por defecto (US-20-02).
  totalDiscount: 0,
  discountedOrders: 0,
  discountCustomerPromo: 0,
  discountTransferAdjustment: 0,
  discountOther: 0,
}

const SUMMARY_WITH_DISCOUNTS = {
  ...BASE_SUMMARY,
  totalDiscount: 3000,
  discountedOrders: 2,
  discountCustomerPromo: 1000,
  discountTransferAdjustment: 2000,
  discountOther: 0,
}

// Turno cerrado a mano, 11h 3m de duración.
const CLOSED_SHIFT = {
  openedAt: '2026-07-23T09:00:00',
  closedAt: '2026-07-23T20:03:00',
  openedBy: 'Juan Pérez',
  closedBy: 'María López',
  autoClose: false,
  summary: BASE_SUMMARY,
}

// ── closedByLabel: resolución del "Cerrado por" (US-20-01) ────

describe('closedByLabel', () => {
  test('turno cerrado a mano → nombre de quien lo cerró', () => {
    expect(closedByLabel(CLOSED_SHIFT)).toBe('María López')
  })

  test('auto-cierre (closedBy null, autoClose true) → "Cierre automático"', () => {
    expect(closedByLabel({ ...CLOSED_SHIFT, closedBy: null, autoClose: true }))
      .toBe('Cierre automático')
  })

  test('auto-cierre gana aunque closedBy venga vacío, no cae en "En curso"', () => {
    // El backend deja closedBy null en el auto-cierre; sin la precedencia de autoClose
    // esto mostraría "En curso", que sería incorrecto para un turno ya cerrado.
    expect(closedByLabel({ autoClose: true, closedBy: null, closedAt: '2026-07-23T20:00:00' }))
      .toBe('Cierre automático')
  })

  test('turno en curso (sin cierre) → "En curso"', () => {
    expect(closedByLabel({ openedBy: 'Juan', closedBy: null, autoClose: false })).toBe('En curso')
  })

  test('shift nulo no rompe', () => {
    expect(closedByLabel(null)).toBe('En curso')
  })
})

// ── formatDuration (US-20-01 usa el helper compartido) ────────

describe('formatDuration', () => {
  test('11h 3m entre apertura y cierre', () => {
    expect(formatDuration('2026-07-23T09:00:00', '2026-07-23T20:03:00')).toBe('11h 3m')
  })

  test('menos de una hora se muestra solo en minutos', () => {
    expect(formatDuration('2026-07-23T09:00:00', '2026-07-23T09:47:00')).toBe('47m')
  })

  test('sin apertura → "—"', () => {
    expect(formatDuration(null, '2026-07-23T20:00:00')).toBe('—')
  })
})

// ── Documento: wiring de los campos nuevos ────────────────────

describe('ShiftSummaryDocument (US-20-01)', () => {
  test('el encabezado incluye duración y quién abrió/cerró', () => {
    const texts = renderTexts(CLOSED_SHIFT, BRANCH)

    expect(texts).toContain('La Roka')
    expect(texts).toContain('Playa Unión')
    expect(texts).toContain('Duración')
    expect(texts).toContain('11h 3m')
    expect(texts).toContain('Abierto por')
    expect(texts).toContain('Juan Pérez')
    expect(texts).toContain('Cerrado por')
    expect(texts).toContain('María López')
  })

  test('el bloque de totales incluye el ticket promedio', () => {
    const texts = renderTexts(CLOSED_SHIFT, BRANCH)

    expect(texts).toContain('Ticket promedio')
    // $11.000 (ARS sin decimales). El separador de miles es NBSP o punto según ICU;
    // se verifica el bloque numérico de forma robusta.
    expect(texts.some(t => /11[.\s]?000/.test(t))).toBe(true)
  })

  test('mantiene el desglose por método de pago', () => {
    const texts = renderTexts(CLOSED_SHIFT, BRANCH)

    expect(texts).toContain('Por método de pago')
    expect(texts).toContain('Efectivo')
    expect(texts).toContain('MercadoPago')
    expect(texts).toContain('QR')
  })

  test('un turno auto-cerrado muestra "Cierre automático" en "Cerrado por"', () => {
    const texts = renderTexts(
      { ...CLOSED_SHIFT, closedBy: null, autoClose: true },
      BRANCH,
    )
    expect(texts).toContain('Cerrado por')
    expect(texts).toContain('Cierre automático')
  })

  test('un turno en curso muestra "En curso" en cierre y en "Cerrado por"', () => {
    const texts = renderTexts(
      { ...CLOSED_SHIFT, closedAt: null, closedBy: null, autoClose: false },
      BRANCH,
    )
    const enCurso = texts.filter(t => t === 'En curso')
    expect(enCurso.length).toBe(2) // fila "Cierre" y fila "Cerrado por"
  })
})

// ── shiftDiscountSummary (US-20-02) ───────────────────────────

describe('shiftDiscountSummary', () => {
  test('sin descuentos → hasDiscounts false y breakdown vacío', () => {
    const r = shiftDiscountSummary(BASE_SUMMARY)
    expect(r.hasDiscounts).toBe(false)
    expect(r.breakdown).toEqual([])
  })

  test('con descuentos → total, cantidad y desglose por motivo (solo montos > 0)', () => {
    const r = shiftDiscountSummary(SUMMARY_WITH_DISCOUNTS)
    expect(r.hasDiscounts).toBe(true)
    expect(r.total).toBe(3000)
    expect(r.count).toBe(2)
    // OTHER es 0 → no aparece; los otros dos con su etiqueta en español.
    expect(r.breakdown).toEqual([
      { label: 'Promoción al cliente', amount: 1000 },
      { label: 'Ajuste por transferencia', amount: 2000 },
    ])
  })

  test('summary nulo no rompe', () => {
    expect(shiftDiscountSummary(null).hasDiscounts).toBe(false)
  })
})

// ── Sección "Descuentos del turno" en el documento (US-20-02) ─

describe('sección de descuentos del PDF', () => {
  test('con descuentos: muestra total, cantidad y desglose por motivo', () => {
    const texts = renderTexts({ ...CLOSED_SHIFT, summary: SUMMARY_WITH_DISCOUNTS }, BRANCH)

    expect(texts).toContain('Descuentos del turno')
    expect(texts).toContain('Total descontado')
    expect(texts).toContain('Pedidos con descuento')
    expect(texts).toContain('Promoción al cliente')
    expect(texts).toContain('Ajuste por transferencia')
    // OTHER en 0 no se lista.
    expect(texts).not.toContain('Otro')
  })

  test('sin descuentos: la sección se omite por completo', () => {
    const texts = renderTexts(CLOSED_SHIFT, BRANCH) // BASE_SUMMARY, sin descuentos

    expect(texts).not.toContain('Descuentos del turno')
    expect(texts).not.toContain('Total descontado')
  })
})

// ── shiftOrderDetailRows (US-20-03) ───────────────────────────

const ORDER_DETAILS = [
  {
    createdAt: '2026-07-23T20:01:00', orderNumber: 5, origin: 'CLIENT',
    paymentMethod: 'CASH', totalAmount: 1600, status: 'DELIVERED',
    discountAmount: 100, discountReason: 'CUSTOMER_PROMO',
  },
  {
    createdAt: '2026-07-23T20:05:00', orderNumber: 6, origin: 'BACKOFFICE',
    paymentMethod: 'MERCADOPAGO', totalAmount: 2000, status: 'DELIVERED',
    discountAmount: null, discountReason: null,
  },
  {
    createdAt: '2026-07-23T20:09:00', orderNumber: 7, origin: 'CLIENT',
    paymentMethod: 'CASH', totalAmount: 800, status: 'CANCELLED',
    discountAmount: null, discountReason: null,
  },
]

describe('shiftOrderDetailRows', () => {
  test('mapea etiquetas (origen, método, estado, motivo) y marca cancelados', () => {
    const { hasOrders, rows } = shiftOrderDetailRows(ORDER_DETAILS)
    expect(hasOrders).toBe(true)
    expect(rows[0]).toMatchObject({
      orderNumber: '#5', origin: 'App', method: 'Efectivo',
      status: 'Entregado', cancelled: false, discount: 'Promoción al cliente',
    })
    expect(rows[1]).toMatchObject({ origin: 'Mostrador', method: 'MercadoPago', discount: '—' })
    expect(rows[2]).toMatchObject({ status: 'Cancelado', cancelled: true })
  })

  test('subtotal por método suma solo DELIVERED (cancelados no ingresan plata)', () => {
    const { methodSubtotals } = shiftOrderDetailRows(ORDER_DETAILS)
    const byLabel = Object.fromEntries(methodSubtotals.map(m => [m.label, m.amount]))
    // Efectivo: solo el entregado de 1600 (el cancelado de 800 no cuenta).
    expect(byLabel.Efectivo).toBe(1600)
    expect(byLabel.MercadoPago).toBe(2000)
    expect(byLabel.QR).toBe(0)
  })

  test('lista vacía o nula → hasOrders false', () => {
    expect(shiftOrderDetailRows([]).hasOrders).toBe(false)
    expect(shiftOrderDetailRows(null).hasOrders).toBe(false)
  })
})

// ── Sección "Detalle de pedidos" en el documento (US-20-03) ───

describe('tabla de detalle del PDF', () => {
  test('con pedidos: encabezados, filas (entregado y cancelado) y subtotales al pie', () => {
    const texts = renderTexts({ ...CLOSED_SHIFT, orderDetails: ORDER_DETAILS }, BRANCH)

    expect(texts).toContain('Detalle de pedidos')
    // Encabezados de columna.
    expect(texts).toContain('Hora')
    expect(texts).toContain('Método')
    expect(texts).toContain('Estado')
    // Estados visibles para diferenciar entregado de cancelado.
    expect(texts).toContain('Entregado')
    expect(texts).toContain('Cancelado')
    // Motivo del descuento en la fila que lo tuvo.
    expect(texts).toContain('Promoción al cliente')
    // Subtotal por método al pie (arqueo).
    expect(texts.some(t => /Efectivo:/.test(t))).toBe(true)
  })

  test('sin detalle de pedidos: la tabla no se renderiza', () => {
    const texts = renderTexts(CLOSED_SHIFT, BRANCH) // sin orderDetails
    expect(texts).not.toContain('Detalle de pedidos')
  })
})
