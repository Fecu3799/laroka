import { render, cleanup, fireEvent, within } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import Orders from '../pages/Orders'
import { OrdersProvider } from '../context/OrdersContext'
import { ShiftProvider } from '../context/ShiftContext'
import { OperatorMessagesProvider } from '../context/OperatorMessagesContext'
import useOrders from '../hooks/useOrders'
import useOrderDetail from '../hooks/useOrderDetail'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useOutletContext: () => ({
      newOrderCount: 0,
      cancelCount: 0,
      resetCounts: vi.fn(),
      setOpenOrderId: vi.fn(),
    }),
  }
})

vi.mock('../hooks/useAuth', () => ({
  default: () => ({ token: 'test-token', branchId: 1, branchName: 'Test', role: 'MANAGER' }),
}))

vi.mock('../hooks/useBranch', () => ({
  default: () => ({ activeBranchId: 1, activeBranchName: 'Test', setActiveBranch: vi.fn() }),
}))

vi.mock('../hooks/useOrders', () => ({ default: vi.fn() }))
vi.mock('../hooks/useOrderDetail', () => ({ default: vi.fn() }))

vi.mock('../services/ordersService', () => ({
  advanceOrderStatus: vi.fn().mockResolvedValue({}),
  applyDiscount: vi.fn().mockResolvedValue(undefined),
  revertDiscount: vi.fn().mockResolvedValue(undefined),
}))

const ORDER_ID = 'aaaa0000-0000-0000-0000-000000000001'

// Subtotal 1000 + envío 500 + servicio 200 = 1700; con 10% de descuento → 1600.
const ORDER = {
  id: ORDER_ID,
  status: 'RECEIVED',
  orderType: 'DELIVERY',
  createdAt: '2024-01-01T10:00:00Z',
  customerName: 'Juan Pérez',
  customerPhone: '2994000001',
  deliveryAddress: 'Calle Falsa 123',
  paymentStatus: 'PENDING',
  paymentMethod: 'CASH',
  items: [{ productName: 'Pizza grande', quantity: 1, unitPrice: 1000 }],
  subtotal: 1000,
  deliveryFee: 500,
  serviceFee: 200,
  totalAmount: 1600,
  notes: null,
  origin: 'BACKOFFICE',
  statusHistory: [],
}

const DISCOUNT = {
  percentage: 10,
  originalTotalAmount: 1700,
  discountAmount: 100,
  finalTotalAmount: 1600,
  reason: 'CUSTOMER_PROMO',
  note: 'cortesía por demora',
  appliedByName: 'Ana Gómez',
  appliedAt: '2026-07-23T20:30:00',
}

const DEFAULT_HOOK = {
  loading: false,
  error: null,
  refresh: vi.fn(),
  clearOrders: vi.fn(),
  dismissOrder: vi.fn(),
  dismissedIds: new Set(),
  updateOrderInList: vi.fn(),
  updatePaymentInList: vi.fn(),
  replaceOrderInList: vi.fn(),
}

/** Abre el panel de detalle con el `detail` que devuelve el endpoint del backoffice. */
function openDetail(detail) {
  useOrders.mockReturnValue({ ...DEFAULT_HOOK, orders: [ORDER] })
  useOrderDetail.mockReturnValue({ detail, refetchDetail: vi.fn() })
  render(
    <MemoryRouter>
      <ShiftProvider>
        <OperatorMessagesProvider>
          <OrdersProvider setOpenOrderId={vi.fn()}>
            <Orders />
          </OrdersProvider>
        </OperatorMessagesProvider>
      </ShiftProvider>
    </MemoryRouter>,
  )
  fireEvent.click(document.querySelector('.orders-row'))
  return document.querySelector('.orders-detail-col')
}

beforeEach(() => {
  vi.clearAllMocks()
  global.fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) })
})

describe('línea de descuento en el detalle (US-19-03)', () => {
  test('muestra el monto descontado y el porcentaje', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })

    const row = panel.querySelector('.detail-discount-row')
    expect(row).toBeInTheDocument()
    expect(within(row).getByText('10%')).toBeInTheDocument()
    // El monto se muestra restando, para que el Total cierre con la suma de arriba.
    expect(row.textContent).toContain('−$100')
  })

  test('la línea va entre los fees y el Total', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })

    const rows = [...panel.querySelectorAll('.detail-subtotal-row, .detail-total-row')]
    const labels = rows.map(r => r.textContent)
    const discountIdx = rows.findIndex(r => r.classList.contains('detail-discount-row'))
    const totalIdx = rows.findIndex(r => r.classList.contains('detail-total-row'))

    expect(discountIdx).toBeGreaterThan(labels.findIndex(t => t.includes('Servicio')))
    expect(discountIdx).toBeLessThan(totalIdx)
  })

  test('el Total mostrado es el post-descuento', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })

    expect(panel.querySelector('.detail-total-amount').textContent).toContain('1.600')
  })

  test('sin descuento no se renderiza ni la línea ni la traza', () => {
    const panel = openDetail({ ...ORDER, totalAmount: 1700, discount: null })

    expect(panel.querySelector('.detail-discount-row')).toBeNull()
    expect(panel.querySelector('.detail-discount-trace')).toBeNull()
  })
})

describe('trazabilidad del descuento (US-19-03)', () => {
  test('muestra motivo en español, quién y cuándo', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })

    const trace = panel.querySelector('.detail-discount-trace')
    expect(within(trace).getByText('Promoción al cliente')).toBeInTheDocument()
    expect(trace.textContent).toContain('Ana Gómez')
  })

  test('muestra la nota cuando existe', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })

    expect(panel.querySelector('.detail-discount-trace-note').textContent)
      .toContain('cortesía por demora')
  })

  test('sin nota, no renderiza el bloque de nota', () => {
    const panel = openDetail({ ...ORDER, discount: { ...DISCOUNT, note: null } })

    expect(panel.querySelector('.detail-discount-trace')).toBeInTheDocument()
    expect(panel.querySelector('.detail-discount-trace-note')).toBeNull()
  })

  test('traduce cada motivo del enum a su etiqueta en español', () => {
    const cases = [
      ['CUSTOMER_PROMO', 'Promoción al cliente'],
      ['TRANSFER_ADJUSTMENT', 'Ajuste por transferencia'],
      ['OTHER', 'Otro'],
    ]
    for (const [reason, label] of cases) {
      const panel = openDetail({ ...ORDER, discount: { ...DISCOUNT, reason } })
      expect(within(panel.querySelector('.detail-discount-trace')).getByText(label))
        .toBeInTheDocument()
      cleanup()
    }
  })

  test('con el usuario borrado muestra el fallback sin romper el detalle', () => {
    const panel = openDetail({ ...ORDER, discount: { ...DISCOUNT, appliedByName: null } })

    const trace = panel.querySelector('.detail-discount-trace')
    expect(trace.textContent).toContain('Usuario eliminado')
    // El monto sigue visible: la traza incompleta no oculta el ajuste de precio.
    expect(panel.querySelector('.detail-discount-row').textContent).toContain('−$100')
  })
})

describe('botón de descuento según haya uno vigente (US-19-05)', () => {
  const btn = panel => panel.querySelector('.detail-action-discount')

  test('sin descuento, el botón dice "Aplicar descuento"', () => {
    const panel = openDetail({ ...ORDER, totalAmount: 1700, discount: null })
    expect(btn(panel).textContent).toBe('Aplicar descuento')
  })

  test('con descuento vigente, el botón pasa a "Modificar descuento"', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })
    expect(btn(panel).textContent).toBe('Modificar descuento')
  })

  test('al modificar, el modal abre precargado con el descuento vigente', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })
    fireEvent.click(btn(panel))

    // El modal se monta en un portal a document.body, fuera del panel.
    const body = within(document.body)
    expect(body.getByRole('heading', { name: 'Modificar descuento' })).toBeInTheDocument()
    expect(body.getByLabelText('Porcentaje').value).toBe('10')
  })
})

describe('borrar descuento (US-19-06)', () => {
  const removeBtn = panel => panel.querySelector('.detail-action-discount-remove')

  test('sin descuento no aparece el botón "Borrar descuento"', () => {
    const panel = openDetail({ ...ORDER, totalAmount: 1700, discount: null })
    expect(removeBtn(panel)).toBeNull()
  })

  test('con descuento vigente conviven "Modificar" y "Borrar"', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })
    expect(panel.querySelector('.detail-action-discount').textContent).toBe('Modificar descuento')
    expect(removeBtn(panel).textContent).toBe('Borrar descuento')
  })

  test('al borrar, abre el modal de reversión mostrando el total restaurado', () => {
    const panel = openDetail({ ...ORDER, discount: DISCOUNT })
    fireEvent.click(removeBtn(panel))

    const body = within(document.body)
    expect(body.getByRole('heading', { name: 'Borrar descuento' })).toBeInTheDocument()
    // El pedido vuelve a subtotal+fees = 1700, no al total descontado.
    expect(body.getByText(/\$1\.700/)).toBeInTheDocument()
  })
})

describe('pedido ya cobrado oculta todos los botones de descuento (US-19-07)', () => {
  const anyDiscountBtn = panel =>
    panel.querySelector('.detail-action-discount, .detail-action-discount-remove')

  test('con un pago en efectivo aprobado no hay botón de descuento, aunque tenga uno vigente', () => {
    // Un pedido marcado como pagado en efectivo: CASH APPROVED con descuento vigente.
    // No debe mostrar "Modificar" ni "Borrar" — el precio ya quedó cobrado.
    const panel = openDetail({
      ...ORDER,
      paymentMethod: 'CASH',
      paymentStatus: 'APPROVED',
      discount: DISCOUNT,
    })
    expect(anyDiscountBtn(panel)).toBeNull()
  })

  test('sin descuento y ya cobrado, tampoco aparece "Aplicar descuento"', () => {
    const panel = openDetail({
      ...ORDER,
      paymentMethod: 'CASH',
      paymentStatus: 'APPROVED',
      totalAmount: 1700,
      discount: null,
    })
    expect(anyDiscountBtn(panel)).toBeNull()
  })
})
