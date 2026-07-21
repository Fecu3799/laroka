import { render, screen, fireEvent, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Orders from '../pages/Orders'
import { OrdersProvider } from '../context/OrdersContext'
import { ShiftProvider } from '../context/ShiftContext'
import { OperatorMessagesProvider } from '../context/OperatorMessagesContext'
import useOrders from '../hooks/useOrders'
import { STATUS_CONFIG } from '../utils/ordersUtils'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useOutletContext: () => ({ newOrderCount: 0, cancelCount: 0, resetCounts: vi.fn(), setOpenOrderId: vi.fn() }) }
})

vi.mock('../hooks/useAuth', () => ({
  default: () => ({ token: 'test-token', branchId: 1, branchName: 'Test' }),
}))

vi.mock('../hooks/useBranch', () => ({
  default: () => ({ activeBranchId: 1, activeBranchName: 'Test', setActiveBranch: vi.fn() }),
}))

vi.mock('../hooks/useOrders', () => ({
  default: vi.fn(),
}))

vi.mock('../hooks/useOrderDetail', () => ({
  default: () => ({ detail: null, refetchDetail: vi.fn() }),
}))

vi.mock('../services/ordersService', () => ({
  advanceOrderStatus: vi.fn().mockResolvedValue({}),
}))

// ── Test fixtures ─────────────────────────────────────────────

const BASE_ORDER = {
  id: 'aaaa0000-0000-0000-0000-000000000001',
  status: 'RECEIVED',
  orderType: 'TAKEAWAY',
  createdAt: '2024-01-01T10:00:00Z',
  customerName: 'Juan Pérez',
  customerPhone: '2994000001',
  deliveryAddress: null,
  paymentStatus: 'PENDING',
  paymentMethod: 'CASH',
  items: [{ productName: 'Pizza grande', quantity: 1, unitPrice: 1000 }],
  totalAmount: 1000,
  subtotal: 1000,
  deliveryFee: 0,
  serviceFee: 0,
  notes: null,
  origin: 'BACKOFFICE',
  statusHistory: [],
}

const ORDER_RECEIVED = BASE_ORDER

const ORDER_IN_PREP_DELIVERY = {
  ...BASE_ORDER,
  id: 'bbbb0000-0000-0000-0000-000000000002',
  status: 'IN_PREPARATION',
  orderType: 'DELIVERY',
  createdAt: '2024-01-01T09:00:00Z',
  customerName: 'María García',
  customerPhone: '2994000002',
  deliveryAddress: 'Calle Falsa 123',
  paymentStatus: 'APPROVED',
  paymentMethod: 'MERCADOPAGO',
}

const ORDER_IN_PREP_TAKEAWAY = {
  ...ORDER_IN_PREP_DELIVERY,
  id: 'cccc0000-0000-0000-0000-000000000003',
  orderType: 'TAKEAWAY',
  deliveryAddress: null,
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

// ── Helpers ───────────────────────────────────────────────────

function hexToRgb(hex) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgb(${r}, ${g}, ${b})`
}

function renderOrders(orders) {
  useOrders.mockReturnValue({ ...DEFAULT_HOOK, orders })
  render(
    <MemoryRouter>
      <ShiftProvider>
        <OperatorMessagesProvider>
          <OrdersProvider setOpenOrderId={vi.fn()}>
            <Orders />
          </OrdersProvider>
        </OperatorMessagesProvider>
      </ShiftProvider>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  global.fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) })
})

// ── Criterion 1: status badge colors ─────────────────────────

describe('status badge colors', () => {
  test.each(Object.entries(STATUS_CONFIG))(
    'badge de %s aplica backgroundColor de STATUS_CONFIG',
    (status, cfg) => {
      renderOrders([{ ...BASE_ORDER, id: `test-id-${status}`, status }])
      const badge = document.querySelector('.status-badge')
      expect(badge).toBeTruthy()
      expect(badge.style.backgroundColor).toBe(hexToRgb(cfg.bg))
      expect(badge.style.color).toBe(hexToRgb(cfg.color))
    }
  )
})

// ── Criterion 3: detail panel ─────────────────────────────────

describe('panel de detalle', () => {
  test('se abre al clickear una fila', () => {
    renderOrders([ORDER_RECEIVED])
    expect(screen.queryByText('ESTADO DEL PEDIDO')).toBeNull()

    fireEvent.click(document.querySelector('.orders-row'))

    expect(screen.getByText('ESTADO DEL PEDIDO')).toBeInTheDocument()
  })

  test('se cierra al clickear el botón X', () => {
    renderOrders([ORDER_RECEIVED])

    fireEvent.click(document.querySelector('.orders-row'))
    expect(screen.getByText('ESTADO DEL PEDIDO')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(screen.queryByText('ESTADO DEL PEDIDO')).toBeNull()
  })

  test('cambia de contenido al clickear otra fila', () => {
    renderOrders([ORDER_RECEIVED, ORDER_IN_PREP_DELIVERY])

    const rows = document.querySelectorAll('.orders-row')

    fireEvent.click(rows[0])
    expect(within(document.querySelector('.orders-detail-col')).getByText('Juan Pérez')).toBeInTheDocument()

    fireEvent.click(rows[1])
    expect(within(document.querySelector('.orders-detail-col')).getByText('María García')).toBeInTheDocument()
  })
})

// ── Criterion 4: Avanzar button label ────────────────────────

describe('botón Avanzar — label según estado y tipo', () => {
  function openPanel(order) {
    renderOrders([order])
    fireEvent.click(document.querySelector('.orders-row'))
  }

  test('RECEIVED → "Avanzar a En preparación"', () => {
    openPanel(ORDER_RECEIVED)
    expect(screen.getByText(/Avanzar a En preparación/)).toBeInTheDocument()
  })

  test('IN_PREPARATION + DELIVERY → "Avanzar a En camino"', () => {
    openPanel(ORDER_IN_PREP_DELIVERY)
    expect(screen.getByText(/Avanzar a En camino/)).toBeInTheDocument()
  })

  test('IN_PREPARATION + TAKEAWAY → "Avanzar a Para retirar"', () => {
    openPanel(ORDER_IN_PREP_TAKEAWAY)
    expect(screen.getByText(/Avanzar a Para retirar/)).toBeInTheDocument()
  })
})

// ── Criterion 5: Atrás button visibility ─────────────────────

describe('botón Atrás', () => {
  function openPanel(order) {
    renderOrders([order])
    fireEvent.click(document.querySelector('.orders-row'))
  }

  test.each(['IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP'])(
    'visible cuando status es %s',
    (status) => {
      openPanel({ ...BASE_ORDER, status })
      expect(screen.getByRole('button', { name: /Atrás/ })).toBeInTheDocument()
    }
  )

  test.each(['RECEIVED', 'PENDING_PAYMENT'])(
    'oculto cuando status es %s',
    (status) => {
      openPanel({ ...BASE_ORDER, status })
      expect(screen.queryByRole('button', { name: /Atrás/ })).toBeNull()
    }
  )
})

// ── Criterion 6: Cancelar button visibility ───────────────────

describe('botón Cancelar', () => {
  function openPanel(order) {
    renderOrders([order])
    fireEvent.click(document.querySelector('.orders-row'))
  }

  test('visible cuando status es RECEIVED', () => {
    openPanel(ORDER_RECEIVED)
    expect(screen.getByRole('button', { name: /Cancelar/ })).toBeInTheDocument()
  })

  test.each(['IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED'])(
    'oculto cuando status es %s',
    (status) => {
      openPanel({ ...BASE_ORDER, status })
      expect(screen.queryByRole('button', { name: /Cancelar/ })).toBeNull()
    }
  )
})

// ── Ítem mitad y mitad (US-HH-04) ─────────────────────────────

describe('ítem mitad y mitad', () => {
  const ORDER_COMBO = {
    ...BASE_ORDER,
    id: 'cccc0000-0000-0000-0000-000000000003',
    items: [
      { productName: 'Muzzarella', secondProductName: 'Calabresa', quantity: 1, unitPrice: 3400 },
      { productName: 'Fugazzeta', secondProductName: null, quantity: 2, unitPrice: 2500 },
    ],
  }

  test('la columna de productos del listado muestra la combinación completa', () => {
    renderOrders([ORDER_COMBO])

    const items = document.querySelector('.col-items')
    expect(items.textContent).toContain('½ Muzzarella + ½ Calabresa')
    expect(items.textContent).toContain('Fugazzeta')
  })

  test('la tabla del panel de detalle muestra la combinación completa', () => {
    renderOrders([ORDER_COMBO])
    fireEvent.click(document.querySelector('.orders-row'))

    const panel = document.querySelector('.orders-detail-col')
    expect(within(panel).getByText('½ Muzzarella + ½ Calabresa')).toBeInTheDocument()
    expect(within(panel).getByText('Fugazzeta')).toBeInTheDocument()
  })

  test('sólo el nombre combinado envuelve en varias líneas', () => {
    renderOrders([ORDER_COMBO])
    fireEvent.click(document.querySelector('.orders-row'))

    const panel = document.querySelector('.orders-detail-col')
    expect(within(panel).getByText('½ Muzzarella + ½ Calabresa'))
      .toHaveClass('detail-prod-name--multiline')
    expect(within(panel).getByText('Fugazzeta'))
      .not.toHaveClass('detail-prod-name--multiline')
  })
})

// ── Ítem con tamaño ───────────────────────────────────────────

describe('ítem con tamaño', () => {
  const ORDER_SIZED = {
    ...BASE_ORDER,
    id: 'dddd0000-0000-0000-0000-000000000004',
    items: [
      { productName: 'Muzzarella', secondProductName: null, sizeName: 'CHICA', quantity: 1, unitPrice: 9000 },
      { productName: 'Napolitana', secondProductName: null, sizeName: null, quantity: 2, unitPrice: 17000 },
    ],
  }

  test('la columna de productos del listado muestra el tamaño', () => {
    renderOrders([ORDER_SIZED])

    const items = document.querySelector('.col-items')
    expect(items.textContent).toContain('Muzzarella (Chica)')
    // Sin tamaño no lleva sufijo: el grande es implícito.
    expect(items.textContent).toContain('Napolitana')
    expect(items.textContent).not.toContain('Napolitana (')
  })

  test('la tabla del panel de detalle muestra el tamaño', () => {
    renderOrders([ORDER_SIZED])
    fireEvent.click(document.querySelector('.orders-row'))

    const panel = document.querySelector('.orders-detail-col')
    expect(within(panel).getByText('Muzzarella (Chica)')).toBeInTheDocument()
    expect(within(panel).getByText('Napolitana')).toBeInTheDocument()
  })
})
