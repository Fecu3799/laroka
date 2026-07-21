import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OrderTrackingBanner } from '../features/order/OrderTrackingBanner'

vi.mock('../hooks/usePreferredBranch', () => ({
  usePreferredBranch: () => ({
    estimatedDeliveryMinutes: 30,
    phone: '2804000000',
    preferredBranchId: 1,
  }),
}))

const KEY = 'laroka_active_orders'

function seedOrder(orderId, branchId = 1) {
  localStorage.setItem(KEY, JSON.stringify([{ orderId, branchId }]))
}

function mockStatus(orderId, payload) {
  vi.stubGlobal('fetch', vi.fn(url => {
    if (url.includes(`/orders/${orderId}/status`)) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(payload) })
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([]) })
  }))
}

describe('OrderTrackingBanner', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders nothing when no active orders', () => {
    const { container } = render(<OrderTrackingBanner branchId={1} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when orders belong to a different branch', async () => {
    seedOrder('order-1', 99)
    mockStatus('order-1', { status: 'IN_PREPARATION', orderType: 'DELIVERY', history: [] })
    let container
    await act(async () => {
      ;({ container } = render(<OrderTrackingBanner branchId={1} />))
    })
    expect(container).toBeEmptyDOMElement()
  })

  it('shows status badge after polling resolves', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'IN_PREPARATION',
      orderType: 'DELIVERY',
      deliveryAddress: 'Av. San Martín 123',
      history: [],
      subtotal: 2000,
      deliveryFee: 500,
      serviceFee: 100,
      totalAmount: 2600,
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(screen.getByText('EN PREPARACIÓN')).toBeInTheDocument()
    })
  })

  it('mantiene el pedido DELIVERED visible con badge y botón Listo, sin removerlo automáticamente', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'DELIVERED',
      orderType: 'DELIVERY',
      history: [],
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(screen.getByText('ENTREGADO')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /listo/i })).toBeInTheDocument()
    })
    // No se removió automáticamente de laroka_active_orders.
    expect(JSON.parse(localStorage.getItem(KEY))).toHaveLength(1)
  })

  it('al presionar Listo descarta el pedido DELIVERED y lo remueve de localStorage', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'DELIVERED',
      orderType: 'DELIVERY',
      history: [],
    })

    const { container } = render(<OrderTrackingBanner branchId={1} />)
    await waitFor(() => screen.getByRole('button', { name: /listo/i }))

    await userEvent.setup().click(screen.getByRole('button', { name: /listo/i }))

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement()
    })
    expect(JSON.parse(localStorage.getItem(KEY) ?? '[]')).toHaveLength(0)
  })

  it('muestra botón Entendido cuando la cancelación fue iniciada por el cliente', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'CANCELLED',
      orderType: 'DELIVERY',
      history: [{ toStatus: 'CANCELLED', cancelledByStaff: false, cancellationReason: null, changedAt: new Date().toISOString() }],
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(screen.getByText('CANCELADO')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /entendido/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /ver motivo/i })).not.toBeInTheDocument()
    })
  })

  it('muestra botón Ver motivo cuando la cancelación fue iniciada por el operador', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'CANCELLED',
      orderType: 'DELIVERY',
      history: [{ toStatus: 'CANCELLED', cancelledByStaff: true, cancellationReason: 'sin stock', changedAt: new Date().toISOString() }],
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(screen.getByText('CANCELADO')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /ver motivo de cancelación/i })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /entendido/i })).not.toBeInTheDocument()
    })
  })

  it('cuando hay entrada CANCELLATION_REQUESTED, muestra Entendido aunque el operador aprobó', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'CANCELLED',
      orderType: 'DELIVERY',
      history: [
        { toStatus: 'CANCELLATION_REQUESTED', cancelledByStaff: false, changedAt: new Date().toISOString() },
        { toStatus: 'CANCELLED', cancelledByStaff: true, cancellationReason: null, changedAt: new Date().toISOString() },
      ],
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /entendido/i })).toBeInTheDocument()
    })
  })

  it('modal de motivo muestra el cancellationReason del operador', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'CANCELLED',
      orderType: 'DELIVERY',
      history: [{ toStatus: 'CANCELLED', cancelledByStaff: true, cancellationReason: 'sin stock disponible', changedAt: new Date().toISOString() }],
    })

    render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => screen.getByText('CANCELADO'))

    await userEvent.setup().click(screen.getByRole('button', { name: /ver motivo de cancelación/i }))

    await waitFor(() => {
      expect(screen.getByText('sin stock disponible')).toBeInTheDocument()
    })
  })

  it('en IN_PREPARATION, al solicitar cancelación muestra la comisión del 15% y el reembolso exacto del 85% (US-17-CF-02)', async () => {
    const user = userEvent.setup()
    seedOrder('order-ip', 1)
    mockStatus('order-ip', {
      status: 'IN_PREPARATION',
      orderType: 'DELIVERY',
      history: [],
      paymentMethod: 'MERCADOPAGO',
      subtotal: 2000,
      deliveryFee: 500,
      serviceFee: 100,
      totalAmount: 2600,
    })

    render(<OrderTrackingBanner branchId={1} />)
    await waitFor(() => screen.getByText('EN PREPARACIÓN'))

    await user.click(screen.getByRole('button', { name: /ver detalle/i }))
    await screen.findByRole('button', { name: /cerrar/i })
    await user.click(screen.getByRole('button', { name: /solicitar cancelación/i }))

    // Mensaje explícito de comisión por cancelación tardía + cifra exacta a devolver:
    // 85% de 2000 = 1700 (mismo cálculo/redondeo que el backend, US-17-03).
    expect(screen.getByText(/comisión por cancelación tardía del 15%/i)).toBeInTheDocument()
    expect(screen.getByText('$1.700')).toBeInTheDocument()
  })

  it('el reembolso del 85% usa redondeo HALF_UP como el backend (subtotal 10.10 → $8,59)', async () => {
    const user = userEvent.setup()
    seedOrder('order-ip2', 1)
    mockStatus('order-ip2', {
      status: 'IN_PREPARATION',
      orderType: 'TAKEAWAY',
      history: [],
      paymentMethod: 'MERCADOPAGO',
      subtotal: 10.10,      // 10.10 * 0.85 = 8.585 → HALF_UP a 2 decimales = 8.59
      deliveryFee: 0,
      serviceFee: 0,
      totalAmount: 10.10,
    })

    render(<OrderTrackingBanner branchId={1} />)
    await waitFor(() => screen.getByText('EN PREPARACIÓN'))

    await user.click(screen.getByRole('button', { name: /ver detalle/i }))
    await screen.findByRole('button', { name: /cerrar/i })
    await user.click(screen.getByRole('button', { name: /solicitar cancelación/i }))

    // 8.585 redondea hacia arriba (HALF_UP), no hacia abajo → $8,59.
    expect(screen.getByText('$8,59')).toBeInTheDocument()
  })

  it('en IN_PREPARATION con pago en EFECTIVO no muestra el bloque de comisión (US-17-CF-02)', async () => {
    const user = userEvent.setup()
    seedOrder('order-cash', 1)
    mockStatus('order-cash', {
      status: 'IN_PREPARATION',
      orderType: 'TAKEAWAY',
      history: [],
      paymentMethod: 'CASH',
      subtotal: 2000,
      deliveryFee: 0,
      serviceFee: 0,
      totalAmount: 2000,
    })

    render(<OrderTrackingBanner branchId={1} />)
    await waitFor(() => screen.getByText('EN PREPARACIÓN'))

    await user.click(screen.getByRole('button', { name: /ver detalle/i }))
    await screen.findByRole('button', { name: /cerrar/i })
    await user.click(screen.getByRole('button', { name: /solicitar cancelación/i }))

    // El modal de confirmación se muestra (nota de solicitud al local + botón),
    // pero SIN el bloque de comisión/monto a devolver (no hay reembolso automático).
    expect(screen.getByText(/la decisión final queda a cargo del local/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /confirmar solicitud/i })).toBeInTheDocument()
    expect(screen.queryByText(/comisión por cancelación tardía/i)).not.toBeInTheDocument()
  })

  it('shows skeleton while order data is loading', () => {
    seedOrder('order-1', 1)
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))) // never resolves

    render(<OrderTrackingBanner branchId={1} />)

    expect(document.querySelector('[aria-busy="true"]')).toBeTruthy()
  })

  it('adds a new order when laroka_orders_updated event fires', async () => {
    vi.stubGlobal('fetch', vi.fn(url => {
      if (url.includes('/status')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            status: 'RECEIVED',
            orderType: 'DELIVERY',
            history: [],
            subtotal: 1000, deliveryFee: 0, serviceFee: 0, totalAmount: 1000,
          }),
        })
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve([]) })
    }))

    render(<OrderTrackingBanner branchId={1} />)
    expect(screen.queryByText('RECIBIDO')).not.toBeInTheDocument()

    act(() => {
      localStorage.setItem(KEY, JSON.stringify([{ orderId: 'order-new', branchId: 1 }]))
      window.dispatchEvent(new Event('laroka_orders_updated'))
    })

    await waitFor(() => {
      expect(screen.getByText('RECIBIDO')).toBeInTheDocument()
    })
  })
})

describe('OrderTrackingBanner — status PENDING_PAYMENT', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  function setup() {
    seedOrder('order-pp', 1)
    mockStatus('order-pp', {
      status: 'PENDING_PAYMENT',
      orderType: 'DELIVERY',
      history: [],
    })
    render(<OrderTrackingBanner branchId={1} />)
  }

  it('muestra el texto "Pago en proceso"', async () => {
    setup()
    await waitFor(() => {
      expect(screen.getByText('Pago en proceso')).toBeInTheDocument()
    })
  })

  it('no renderiza la barra de progreso', async () => {
    setup()
    await waitFor(() => screen.getByText('Pago en proceso'))
    expect(document.querySelector('[data-testid="progress-bar"]')).not.toBeInTheDocument()
  })

  it('no renderiza el tiempo estimado de entrega', async () => {
    setup()
    await waitFor(() => screen.getByText('Pago en proceso'))
    expect(screen.queryByText(/llega en/i)).not.toBeInTheDocument()
  })

  it('renderiza el botón Ver detalle', async () => {
    setup()
    await waitFor(() => screen.getByText('Pago en proceso'))
    expect(screen.getByRole('button', { name: /ver detalle/i })).toBeInTheDocument()
  })

  it('clicking Ver detalle abre el modal de detalle', async () => {
    const user = userEvent.setup()
    setup()
    await waitFor(() => screen.getByText('Pago en proceso'))

    await user.click(screen.getByRole('button', { name: /ver detalle/i }))

    expect(screen.getByRole('button', { name: /cerrar/i })).toBeInTheDocument()
  })

  it('en el modal, clicking "Cancelar pedido" muestra la confirmación inline', async () => {
    const user = userEvent.setup()
    setup()
    await waitFor(() => screen.getByText('Pago en proceso'))

    await user.click(screen.getByRole('button', { name: /ver detalle/i }))
    await screen.findByRole('button', { name: /cerrar/i })

    await user.click(screen.getByRole('button', { name: /^cancelar pedido$/i }))

    expect(screen.getByText(/el pedido será cancelado y no podrá reactivarse/i)).toBeInTheDocument()
  })
})

describe('OrderTrackingBanner — ítem mitad y mitad (US-HH-F-02)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const STATUS = {
    orderId: 'order-hh',
    status: 'IN_PREPARATION',
    orderType: 'TAKEAWAY',
    subtotal: 5900,
    serviceFee: 0,
    deliveryFee: 0,
    totalAmount: 5900,
    history: [],
  }

  // GET /orders/{id}/items devuelve secondProductName sólo en los ítems combinados.
  function mockStatusAndItems(items) {
    vi.stubGlobal('fetch', vi.fn(url => {
      if (String(url).includes('/items')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(items) })
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve(STATUS) })
    }))
  }

  async function openDetail() {
    seedOrder('order-hh')
    render(<OrderTrackingBanner branchId={1} />)
    const btn = await screen.findByRole('button', { name: /ver detalle/i })
    await act(async () => {
      await userEvent.click(btn)
    })
  }

  it('muestra la combinación completa de un ítem mitad y mitad', async () => {
    mockStatusAndItems([
      { name: 'Muzzarella', secondProductName: 'Calabresa', quantity: 1, unitPrice: 3400, subtotal: 3400 },
    ])
    await openDetail()

    await waitFor(() => {
      expect(screen.getByText(/1×\s*½ Muzzarella \+ ½ Calabresa/)).toBeInTheDocument()
    })
  })

  it('un ítem simple sigue mostrando sólo su nombre', async () => {
    mockStatusAndItems([
      { name: 'Fugazzeta', secondProductName: null, quantity: 2, unitPrice: 2500, subtotal: 5000 },
    ])
    await openDetail()

    await waitFor(() => {
      expect(screen.getByText(/2×\s*Fugazzeta/)).toBeInTheDocument()
    })
    expect(screen.queryByText(/½/)).not.toBeInTheDocument()
  })
})

describe('OrderTrackingBanner — ítem con tamaño (US-SIZE)', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const STATUS = {
    orderId: 'order-size',
    status: 'IN_PREPARATION',
    orderType: 'TAKEAWAY',
    subtotal: 1900,
    serviceFee: 0,
    deliveryFee: 0,
    totalAmount: 1900,
    history: [],
  }

  function mockStatusAndItems(items) {
    vi.stubGlobal('fetch', vi.fn(url => {
      if (String(url).includes('/items')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(items) })
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve(STATUS) })
    }))
  }

  async function openDetail() {
    seedOrder('order-size')
    render(<OrderTrackingBanner branchId={1} />)
    const btn = await screen.findByRole('button', { name: /ver detalle/i })
    await act(async () => {
      await userEvent.click(btn)
    })
  }

  it('muestra el tamaño elegido en el ítem', async () => {
    mockStatusAndItems([
      { name: 'Muzzarella', secondProductName: null, sizeName: 'CHICA', quantity: 1, unitPrice: 1900, subtotal: 1900 },
    ])
    await openDetail()

    await waitFor(() => {
      expect(screen.getByText(/1×\s*Muzzarella \(Chica\)/)).toBeInTheDocument()
    })
  })

  it('un ítem sin tamaño no gana sufijo', async () => {
    // El grande es implícito: viaja sin sizeName y se muestra como siempre.
    mockStatusAndItems([
      { name: 'Muzzarella', secondProductName: null, sizeName: null, quantity: 2, unitPrice: 15000, subtotal: 30000 },
    ])
    await openDetail()

    await waitFor(() => {
      expect(screen.getByText(/2×\s*Muzzarella/)).toBeInTheDocument()
    })
    expect(screen.queryByText(/\(/)).not.toBeInTheDocument()
  })
})
