import { render, screen, within, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import NewOrderModal from '../components/NewOrderModal'
import { fetchBranchMenu } from '../services/catalogService'
import { fetchBranches } from '../services/branchService'
import { createBackofficeOrder } from '../services/ordersService'

vi.mock('../hooks/useAuth', () => ({ default: () => ({ token: 'tok' }) }))
vi.mock('../hooks/useBranch', () => ({ default: () => ({ activeBranchId: 1 }) }))
vi.mock('../services/catalogService', () => ({ fetchBranchMenu: vi.fn() }))
vi.mock('../services/branchService', () => ({ fetchBranches: vi.fn() }))
vi.mock('../services/ordersService', () => ({ createBackofficeOrder: vi.fn() }))

const MENU = [
  {
    categoryName: 'Pizzas',
    products: [
      { id: 10, name: 'Muzzarella', price: 2800, available: true },
      { id: 20, name: 'Napolitana', price: 3200, available: false },
    ],
  },
]

beforeEach(() => {
  vi.clearAllMocks()
  fetchBranchMenu.mockResolvedValue(MENU)
  fetchBranches.mockResolvedValue([{ id: 1, deliveryFee: 500, serviceFee: 100 }])
  createBackofficeOrder.mockResolvedValue({ orderId: 'o-1' })
})

async function renderAndLoad() {
  render(<NewOrderModal open onClose={vi.fn()} />)
  // Espera a que cargue el menú de la sucursal.
  await screen.findByText('Muzzarella')
}

describe('NewOrderModal · advertencia de productos no disponibles (US-15-F-09)', () => {
  test('el selector consume available y muestra badge junto al producto no disponible', async () => {
    await renderAndLoad()
    const napoRow = screen.getByText('Napolitana').closest('.nom-product-row')
    expect(within(napoRow).getByText('No disponible')).toBeInTheDocument()
    // El disponible no lleva badge.
    const muzzaRow = screen.getByText('Muzzarella').closest('.nom-product-row')
    expect(within(muzzaRow).queryByText('No disponible')).not.toBeInTheDocument()
  })

  test('confirmar con un no disponible abre el modal de confirmación antes del POST', async () => {
    await renderAndLoad()

    fireEvent.click(screen.getByLabelText('Agregar Napolitana'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    // Aparece el modal con el nombre del producto; el POST aún no se dispara.
    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText('Napolitana')).toBeInTheDocument()
    expect(createBackofficeOrder).not.toHaveBeenCalled()
  })

  test('cancelar vuelve al formulario sin perder los datos ni disparar el POST', async () => {
    await renderAndLoad()

    fireEvent.click(screen.getByLabelText('Agregar Napolitana'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    fireEvent.click(screen.getByRole('button', { name: /^cancelar$/i }))

    // El modal se cierra, el POST no se disparó y el producto sigue en el carrito.
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(createBackofficeOrder).not.toHaveBeenCalled()
    const selected = screen.getByText('PRODUCTOS SELECCIONADOS').closest('.nom-items-section')
    expect(within(selected).getByText('Napolitana')).toBeInTheDocument()
  })

  test('confirmar de todas formas dispara el POST con origin BACKOFFICE', async () => {
    await renderAndLoad()

    fireEvent.click(screen.getByLabelText('Agregar Napolitana'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    fireEvent.click(screen.getByRole('button', { name: /confirmar de todas formas/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalledTimes(1))
    expect(createBackofficeOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        origin: 'BACKOFFICE',
        items: [{ productId: 20, quantity: 1 }],
      }),
      'tok',
    )
  })

  test('un pedido solo con productos disponibles no abre el modal y postea directo', async () => {
    await renderAndLoad()

    fireEvent.click(screen.getByLabelText('Agregar Muzzarella'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(createBackofficeOrder).toHaveBeenCalledWith(
      expect.objectContaining({ items: [{ productId: 10, quantity: 1 }] }),
      'tok',
    )
  })
})

describe('NewOrderModal · mitad y mitad (US-HH-F-03)', () => {
  // Dos categorías: una admite mitad y mitad, la otra no. Ambas con productos disponibles.
  const MENU_HH = [
    {
      categoryName: 'Pizzas',
      allowsHalfAndHalf: true,
      products: [
        { id: 10, name: 'Muzzarella', price: 2800, available: true },
        { id: 11, name: 'Calabresa', price: 3400, available: true },
        { id: 12, name: 'Napolitana', price: 3200, available: false },
      ],
    },
    {
      categoryName: 'Bebidas',
      allowsHalfAndHalf: false,
      products: [{ id: 30, name: 'Gaseosa', price: 1500, available: true }],
    },
  ]

  // Scopeadas al catálogo: una vez que el producto está en el carrito, su nombre aparece
  // dos veces en el DOM.
  const catalog = () => within(document.querySelector('.nom-col-left'))

  const row = name => catalog().getByText(name).closest('.nom-product-row')

  const halfBtn = name =>
    within(row(name)).queryByRole('button', { name: /mitad y mitad/i })

  async function renderHH() {
    fetchBranchMenu.mockResolvedValue(MENU_HH)
    render(<NewOrderModal open onClose={vi.fn()} />)
    await screen.findByText('Muzzarella')
  }

  test('el botón ½ aparece sólo en categorías que admiten mitad y mitad', async () => {
    await renderHH()

    expect(halfBtn('Muzzarella')).toBeInTheDocument()
    expect(halfBtn('Calabresa')).toBeInTheDocument()
    expect(halfBtn('Gaseosa')).toBeNull()
    // Un producto no disponible sí puede ser mitad: el backoffice advierte, no bloquea.
    expect(halfBtn('Napolitana')).toBeInTheDocument()
  })

  test('una mitad no disponible se puede combinar y cae en la advertencia al confirmar', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Napolitana'))   // Napolitana está marcada no disponible

    expect(screen.getByText('½ Muzzarella + ½ Napolitana')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    // Mismo flujo que un producto entero no disponible (US-15-F-09): advierte y no postea.
    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText('½ Muzzarella + ½ Napolitana')).toBeInTheDocument()
    expect(createBackofficeOrder).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /confirmar de todas formas/i }))
    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalledTimes(1))
    expect(createBackofficeOrder.mock.calls[0][0].items).toEqual([
      { productId: 10, secondProductId: 12, quantity: 1 },
    ])
  })

  test('un combinado con ambas mitades disponibles no dispara la advertencia', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Calabresa'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalledTimes(1))
  })

  test('al marcar la primera mitad sólo quedan habilitadas las candidatas', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))

    expect(row('Calabresa')).not.toHaveClass('nom-product-row--dimmed')
    expect(row('Gaseosa')).toHaveClass('nom-product-row--dimmed')
    // La pendiente queda marcada, no atenuada: tocarla de nuevo cancela.
    expect(row('Muzzarella')).toHaveClass('nom-product-row--half-pending')
    expect(row('Muzzarella')).not.toHaveClass('nom-product-row--dimmed')
  })

  test('se completa tocando el ½ de la otra pizza', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Calabresa'))

    expect(screen.getByText('½ Muzzarella + ½ Calabresa')).toBeInTheDocument()
  })

  test('se completa tocando la fila de la otra pizza', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(row('Calabresa'))

    expect(screen.getByText('½ Muzzarella + ½ Calabresa')).toBeInTheDocument()
  })

  test('el precio del combinado es el de la mitad más cara', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Calabresa'))

    // Muzzarella 2800 + Calabresa 3400 → 3400, no la suma ni el promedio.
    const cartItem = screen.getByText('½ Muzzarella + ½ Calabresa').closest('.nom-cart-item')
    expect(within(cartItem).getByText('$3.400')).toBeInTheDocument()
  })

  test('volver a tocar el ½ de la misma pizza cancela la combinación', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Muzzarella'))

    expect(row('Gaseosa')).not.toHaveClass('nom-product-row--dimmed')
    expect(screen.queryByText(/½ Muzzarella/)).not.toBeInTheDocument()
  })

  test('el combinado no se fusiona con el producto suelto de su primera mitad', async () => {
    await renderHH()
    fireEvent.click(row('Muzzarella'))          // Muzzarella suelta
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Calabresa'))       // ½ Muzza + ½ Calabresa

    const cart = document.querySelectorAll('.nom-cart-item')
    expect(cart).toHaveLength(2)
    const names = [...cart].map(el => el.querySelector('.nom-cart-name').textContent)
    expect(names).toEqual(['Muzzarella', '½ Muzzarella + ½ Calabresa'])
  })

  test('el pedido viaja con secondProductId en el ítem combinado', async () => {
    await renderHH()
    fireEvent.click(halfBtn('Muzzarella'))
    fireEvent.click(halfBtn('Calabresa'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalled())
    const payload = createBackofficeOrder.mock.calls[0][0]
    expect(payload.items).toEqual([
      { productId: 10, secondProductId: 11, quantity: 1 },
    ])
  })

  test('un ítem simple sigue viajando sin secondProductId', async () => {
    await renderHH()
    fireEvent.click(row('Muzzarella'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalled())
    expect(createBackofficeOrder.mock.calls[0][0].items).toEqual([
      { productId: 10, quantity: 1 },
    ])
  })
})
