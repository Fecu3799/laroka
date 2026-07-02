import { render, screen, within, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import Menu from '../pages/Menu'
import useAuth from '../hooks/useAuth'
import { useConfig } from '../context/ConfigContext'
import { fetchBranchMenu } from '../services/catalogService'

vi.mock('../hooks/useAuth', () => ({ default: vi.fn() }))
vi.mock('../context/ConfigContext', () => ({ useConfig: vi.fn() }))
vi.mock('../services/catalogService', () => ({
  fetchBranchMenu: vi.fn(),
  updateProductAvailability: vi.fn(),
  deleteCategory: vi.fn(),
  deleteProduct: vi.fn(),
}))

const CATEGORIES = [{ id: 1, name: 'Pizzas', productCount: 2 }]
const PRODUCTS = [
  { id: 10, name: 'Muzzarella', price: 2800, categoryId: 1 },
  { id: 20, name: 'Napolitana', price: 3200, categoryId: 1 },
]

function toggleFor(productName) {
  const row = screen.getByText(productName).closest('.menu-product-row')
  return within(row).getByRole('checkbox')
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ token: 'tok', role: 'MANAGER', branchId: 1 })
  useConfig.mockReturnValue({
    categories: CATEGORIES,
    products: PRODUCTS,
    loadingConfig: false,
    reloadCategories: vi.fn(),
    reloadProducts: vi.fn(),
  })
})

describe('Menu · disponibilidad por sucursal (MANAGER)', () => {
  // Blindaje del bug US-15: GET /branches/{id}/menu devuelve TODOS los productos con
  // el flag available (US-15-11). El toggle debe reflejar available directamente, no la
  // mera presencia del producto en la respuesta.
  test('un producto con available=false en el menú se muestra con el toggle apagado', async () => {
    fetchBranchMenu.mockResolvedValue([
      {
        categoryId: 1,
        categoryName: 'Pizzas',
        products: [
          { id: 10, name: 'Muzzarella', price: 2800, available: true },
          { id: 20, name: 'Napolitana', price: 3200, available: false },
        ],
      },
    ])

    render(<Menu />)

    // El disponible pasa a encendido cuando resuelve el menú...
    await waitFor(() => expect(toggleFor('Muzzarella')).toBeChecked())
    // ...y el no disponible queda apagado, pese a estar presente en la respuesta.
    expect(toggleFor('Napolitana')).not.toBeChecked()

    expect(fetchBranchMenu).toHaveBeenCalledWith(1, 'tok')
  })
})
