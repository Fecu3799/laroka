import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CartScreen } from '../components/CartScreen'

vi.mock('../services/paymentsService', () => ({
  initiatePayment: vi.fn(() => new Promise(() => {})),
}))

vi.mock('../hooks/usePreferredBranch', () => ({
  usePreferredBranch: () => ({
    preferredBranchId: 1,
    deliveryFee: 500,
    serviceFee: 100,
  }),
}))

vi.stubGlobal('fetch', vi.fn())

const ITEMS = [
  { id: 1, name: 'Muzarella', price: 2000, qty: 2, imageUrl: null },
  { id: 2, name: 'Napolitana', price: 1500, qty: 1, imageUrl: null },
]

function renderCart(items = ITEMS, overrides = {}) {
  const props = {
    items,
    extras: [],
    onBack: vi.fn(),
    onRemove: vi.fn(),
    onUpdateQty: vi.fn(),
    onClear: vi.fn(),
    onAddExtra: vi.fn(),
    ...overrides,
  }
  render(<CartScreen {...props} />)
  return props
}

describe('CartScreen', () => {
  it('renders item names', () => {
    renderCart()
    expect(screen.getByText('Muzarella')).toBeInTheDocument()
    expect(screen.getByText('Napolitana')).toBeInTheDocument()
  })

  it('shows empty state when cart is empty', () => {
    renderCart([])
    expect(screen.getByText('Tu carrito está vacío')).toBeInTheDocument()
  })

  it('minus button is disabled when qty is 1', () => {
    const items = [{ id: 1, name: 'Muzarella', price: 2000, qty: 1, imageUrl: null }]
    renderCart(items)
    const minus = screen.getByRole('button', { name: 'Reducir cantidad' })
    expect(minus).toBeDisabled()
  })

  it('plus button calls onUpdateQty with qty + 1', async () => {
    const user = userEvent.setup()
    const { onUpdateQty } = renderCart()
    await user.click(screen.getAllByRole('button', { name: 'Aumentar cantidad' })[0])
    expect(onUpdateQty).toHaveBeenCalledWith(1, 3)
  })

  it('minus button calls onUpdateQty with qty - 1 when qty > 1', async () => {
    const user = userEvent.setup()
    const { onUpdateQty } = renderCart()
    await user.click(screen.getAllByRole('button', { name: 'Reducir cantidad' })[0])
    expect(onUpdateQty).toHaveBeenCalledWith(1, 1)
  })

  it('delete button calls onRemove after 240ms animation delay', () => {
    vi.useFakeTimers()
    const { onRemove } = renderCart()
    fireEvent.click(screen.getByRole('button', { name: 'Eliminar Muzarella' }))
    expect(onRemove).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(240))
    expect(onRemove).toHaveBeenCalledWith(1)
    vi.useRealTimers()
  })

  it('displays subtotal formatted as $X', () => {
    renderCart()
    // 2000*2 + 1500*1 = 5500
    expect(screen.getByText('$5.500')).toBeInTheDocument()
  })
})

describe('CartScreen — paymentFailure recovery', () => {
  const FAILURE_DATA = {
    orderId: 'order-42',
    formData: {
      orderType: 'takeaway',
      nombre: 'Juan',
      telefono: '1122334455',
      direccion: '',
      notas: '',
    },
  }

  beforeEach(() => sessionStorage.clear())
  afterEach(() => sessionStorage.clear())

  it('muestra el checkout desde el primer render cuando paymentFailure está presente', () => {
    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })
    expect(screen.getByText('TUS DATOS')).toBeInTheDocument()
  })

  it('muestra PendingPaymentModal desde el primer render cuando paymentFailure está presente', () => {
    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Tenés un pago pendiente')).toBeInTheDocument()
  })

  it('persiste laroka_checkout_recovery en sessionStorage al confirmar con MercadoPago', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ orderId: 'order-99' }),
    })

    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })

    await user.click(screen.getByRole('button', { name: /mercadopago/i }))
    await user.click(screen.getByRole('button', { name: /ir a pagar/i }))

    await waitFor(() => {
      const raw = sessionStorage.getItem('laroka_checkout_recovery')
      expect(raw).not.toBeNull()
      const recovery = JSON.parse(raw)
      expect(recovery.orderId).toBe('order-99')
      expect(recovery.formData.nombre).toBe('Juan')
      expect(recovery.items).toHaveLength(ITEMS.length)
    })
  })
})
