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
