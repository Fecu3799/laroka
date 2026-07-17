import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CartScreen } from '../pages/CartScreen'

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
    // 1) verificación de acceptingOrders del branch, 2) creación del pedido
    vi.mocked(fetch)
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ acceptingOrders: true }),
      })
      .mockResolvedValueOnce({
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

// US-15-CF-05 / US-15-09: si un producto del carrito se desactivó, el backend
// rechaza la creación del pedido con 422 nombrando el producto. El carrito debe
// removerlo automáticamente para que el reintento no vuelva a fallar por lo mismo.
describe('CartScreen — producto no disponible al confirmar (US-15-CF-05)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(fetch).mockReset()
    delete globalThis.Notification
  })
  afterEach(() => sessionStorage.clear())

  async function goToCheckoutAndConfirm() {
    const user = userEvent.setup()
    const props = renderCart(ITEMS)
    // Ir al checkout desde el carrito.
    await user.click(screen.getByRole('button', { name: /ir a pagar/i }))
    // Retirar (takeaway) para no requerir dirección; efectivo es el default.
    await user.click(screen.getByRole('button', { name: /retirar/i }))
    await user.type(screen.getByPlaceholderText(/cómo te llamás/i), 'Juan')
    await user.type(screen.getByPlaceholderText('11 0000-0000'), '1122334455')
    await user.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    return props
  }

  it('remueve del carrito el producto nombrado en el 422 y no vacía el carrito', async () => {
    // 1) verificación acceptingOrders del branch, 2) POST /orders → 422.
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ acceptingOrders: true }) })
      .mockResolvedValueOnce({
        ok: false,
        status: 422,
        json: () => Promise.resolve({
          message: 'El producto no está disponible: Napolitana',
          productId: 2,
        }),
      })

    const props = await goToCheckoutAndConfirm()

    // El productId (2) del body estructurado se remueve; el carrito NO se vacía.
    await waitFor(() => expect(props.onRemove).toHaveBeenCalledWith(2))
    expect(props.onClear).not.toHaveBeenCalled()
  })

  it('vuelve a la pantalla del carrito tras el 422 con productId (US-17-CF-01)', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ acceptingOrders: true }) })
      .mockResolvedValueOnce({
        ok: false,
        status: 422,
        json: () => Promise.resolve({
          message: 'El producto no está disponible: Napolitana',
          productId: 2,
        }),
      })

    const props = await goToCheckoutAndConfirm()

    // Se remueve el producto...
    await waitFor(() => expect(props.onRemove).toHaveBeenCalledWith(2))
    // ...y se navega de vuelta al carrito: el checkout se desmonta (su campo de
    // nombre desaparece) y reaparece el botón "IR A PAGAR" propio del carrito.
    await waitFor(() =>
      expect(screen.queryByPlaceholderText(/cómo te llamás/i)).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: /ir a pagar/i })).toBeInTheDocument()
  })

  it('no remueve nada si el 422 es por otra regla de negocio', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ acceptingOrders: true }) })
      .mockResolvedValueOnce({
        ok: false,
        status: 422,
        json: () => Promise.resolve({ message: 'No hay turno activo para esta sucursal' }),
      })

    const props = await goToCheckoutAndConfirm()

    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2))
    expect(props.onRemove).not.toHaveBeenCalled()
    expect(props.onClear).not.toHaveBeenCalled()
  })
})

// US-09-F-04: en iOS el Web Push solo funciona desde la PWA instalada. Al
// confirmar el pedido sobre iOS Safari (no standalone), CartScreen debe mostrar
// las instrucciones de instalación SIN llamar a requestPermission(), y no debe
// reabrirlas si ya se mostraron en la misma sesión.
describe('CartScreen — iOS Safari no instalado (US-09-F-04)', () => {
  const INSTALL_KEY = 'laroka_push_install_shown'
  const IOS_UA =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1'
  const INSTALL_TEXT = /instalá la app desde Safari usando el ícono compartir/i

  const FAILURE_DATA = {
    orderId: 'order-ios',
    formData: { orderType: 'takeaway', nombre: 'Juan', telefono: '1122334455', direccion: '', notas: '' },
  }

  const ORIGINAL_UA = navigator.userAgent

  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(fetch).mockReset()
    // UA de iPhone; navigator.standalone queda undefined → !standalone = true,
    // es decir, Safari (no instalado como PWA).
    Object.defineProperty(navigator, 'userAgent', { value: IOS_UA, configurable: true })
    // Notification existe (typeof !== 'undefined') pero NO debe invocarse en iOS.
    globalThis.Notification = { permission: 'default', requestPermission: vi.fn() }
  })

  afterEach(() => {
    Object.defineProperty(navigator, 'userAgent', { value: ORIGINAL_UA, configurable: true })
    delete globalThis.Notification
    sessionStorage.clear()
  })

  it('muestra las instrucciones de instalación sin llamar a requestPermission()', async () => {
    const user = userEvent.setup()
    // CheckoutScreen verifica acceptingOrders del branch antes de onConfirm.
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ acceptingOrders: true }),
    })

    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })

    // efectivo es el método por defecto → botón "CONFIRMAR PEDIDO".
    await user.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    // Aparece el bottom sheet de instalación con el texto de US-09-F-04.
    expect(await screen.findByText(INSTALL_TEXT)).toBeInTheDocument()
    // En iOS Safari nunca se pide permiso (fallaría en silencio).
    expect(globalThis.Notification.requestPermission).not.toHaveBeenCalled()
  })

  it('muestra las instrucciones aunque la API Notification no exista (iOS Safari sin PWA)', async () => {
    const user = userEvent.setup()
    // Caso real del bug: en iOS Safari sin instalar, la API Notification no
    // existe. La detección de iOS debe correr ANTES del chequeo de soporte, o el
    // sheet de instalación nunca se mostraría.
    delete globalThis.Notification
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ acceptingOrders: true }),
    })

    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })

    await user.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    expect(await screen.findByText(INSTALL_TEXT)).toBeInTheDocument()
  })

  it('no reabre las instrucciones si ya se mostraron en la sesión y crea el pedido', async () => {
    const user = userEvent.setup()
    // Marca de "ya mostrado en esta sesión".
    sessionStorage.setItem(INSTALL_KEY, '1')
    // 1) acceptingOrders del branch, 2) creación del pedido (el flujo continúa
    //    porque el sheet no vuelve a mostrarse).
    vi.mocked(fetch)
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ acceptingOrders: true }) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ orderId: 'order-ios' }) })

    renderCart(ITEMS, { paymentFailure: FAILURE_DATA })
    await user.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    // El flujo avanza hasta crear el pedido (branches + POST /orders = 2 fetches).
    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2))
    // El sheet no se mostró y no se pidió permiso.
    expect(screen.queryByText(INSTALL_TEXT)).not.toBeInTheDocument()
    expect(globalThis.Notification.requestPermission).not.toHaveBeenCalled()
  })
})
