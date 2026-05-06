import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import { OrderTrackingBanner } from '../components/OrderTrackingBanner'

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

  it('removes order from banner after DELIVERED status', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'DELIVERED',
      orderType: 'DELIVERY',
      history: [],
    })

    const { container } = render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement()
    })
  })

  it('removes order from banner after CANCELLED status', async () => {
    seedOrder('order-1', 1)
    mockStatus('order-1', {
      status: 'CANCELLED',
      orderType: 'DELIVERY',
      history: [],
    })

    const { container } = render(<OrderTrackingBanner branchId={1} />)

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement()
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
