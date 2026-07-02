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
