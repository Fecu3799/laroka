import { render, screen, within, waitFor, fireEvent } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import ProductDrawer from '../components/ProductDrawer'
import useAuth from '../hooks/useAuth'
import {
  fetchCategoryTypes,
  fetchProductBranchConfig,
  fetchProductSizes,
  createProductSize,
  updateProductSize,
  updateProductSizeBranchConfig,
} from '../services/catalogService'

vi.mock('../hooks/useAuth', () => ({ default: vi.fn() }))
vi.mock('../services/catalogService', () => ({
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  fetchProductBranchConfig: vi.fn(),
  updateProductBranchConfig: vi.fn(),
  updateProductPrice: vi.fn(),
  fetchCategoryTypes: vi.fn(),
  fetchProductSizes: vi.fn(),
  createProductSize: vi.fn(),
  updateProductSize: vi.fn(),
  updateProductSizeBranchConfig: vi.fn(),
}))

// Categoría 1 = Pizza (admite tamaños); categoría 2 = Bebida (no).
const CATEGORIES = [
  { id: 1, name: 'Pizzas', categoryTypeId: 100 },
  { id: 2, name: 'Bebidas', categoryTypeId: 200 },
]
const CATEGORY_TYPES = [
  { id: 100, name: 'Pizza', allowsHalfAndHalf: true, allowsSizes: true },
  { id: 200, name: 'Bebida', allowsHalfAndHalf: false, allowsSizes: false },
]

const PRODUCT = { id: 10, name: 'Muzzarella', price: 15000, categoryId: 1, description: '', imageUrl: '' }

const CHICA = { id: 50, productId: 10, size: 'CHICA', price: 9000, active: true }

// Config por sucursal con los campos de tamaño de US-SIZE-F-01.
const BRANCH_ROWS = [
  {
    branchId: 1, branchName: 'Centro', available: true, priceOverride: null, effectivePrice: 15000,
    productSizeId: 50, sizePriceOverride: 9900, sizeEffectivePrice: 9900,
  },
  {
    branchId: 2, branchName: 'Norte', available: true, priceOverride: null, effectivePrice: 15000,
    productSizeId: 50, sizePriceOverride: null, sizeEffectivePrice: 9000,
  },
]

beforeEach(() => {
  vi.clearAllMocks()
  useAuth.mockReturnValue({ token: 'tok', tenantId: 1, role: 'ADMIN', branchId: null })
  fetchCategoryTypes.mockResolvedValue(CATEGORY_TYPES)
  fetchProductBranchConfig.mockResolvedValue(BRANCH_ROWS)
  fetchProductSizes.mockResolvedValue([CHICA])
  createProductSize.mockResolvedValue(CHICA)
  updateProductSize.mockResolvedValue(CHICA)
  updateProductSizeBranchConfig.mockResolvedValue(undefined)
})

function renderDrawer(overrides = {}) {
  const props = {
    open: true,
    mode: 'edit',
    product: PRODUCT,
    categories: CATEGORIES,
    onClose: vi.fn(),
    onSaved: vi.fn(),
    ...overrides,
  }
  render(<ProductDrawer {...props} />)
  return props
}

const sizeSection = () => screen.queryByText('Tamaño chica')
const sizeInput = () => screen.getByLabelText('Precio chica')

describe('ProductDrawer · tamaño chica (US-SIZE-F-01)', () => {
  test('la sección aparece sólo si la categoría admite tamaños', async () => {
    renderDrawer()

    await waitFor(() => expect(sizeSection()).toBeInTheDocument())
  })

  test('no aparece para una categoría que no admite tamaños', async () => {
    renderDrawer({ product: { ...PRODUCT, categoryId: 2 } })

    await waitFor(() => expect(fetchCategoryTypes).toHaveBeenCalled())
    expect(sizeSection()).not.toBeInTheDocument()
  })

  test('no aparece al crear un producto (todavía no tiene id)', async () => {
    renderDrawer({ mode: 'create', product: null })

    await waitFor(() => expect(fetchCategoryTypes).toHaveBeenCalled())
    expect(sizeSection()).not.toBeInTheDocument()
    expect(fetchProductSizes).not.toHaveBeenCalled()
  })

  test('no hay forma de elegir qué tamaño cargar: siempre es CHICA', async () => {
    // El grande es implícito (su precio es el precio base del producto), así que la UI no
    // ofrece ningún selector de tamaño y el alta manda CHICA fijo.
    fetchProductSizes.mockResolvedValue([])
    renderDrawer()
    await waitFor(() => expect(sizeSection()).toBeInTheDocument())

    const section = sizeSection().closest('.pbc-section')
    expect(within(section).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(section).queryByRole('radio')).not.toBeInTheDocument()

    fireEvent.change(sizeInput(), { target: { value: '9500' } })
    fireEvent.blur(sizeInput())

    await waitFor(() => expect(createProductSize).toHaveBeenCalled())
    expect(createProductSize.mock.calls[0][1].size).toBe('CHICA')
  })

  test('carga el precio existente del tamaño', async () => {
    renderDrawer()

    await waitFor(() => expect(sizeInput()).toHaveValue(9000))
  })

  test('sin tamaño cargado, crea la fila CHICA al escribir un precio', async () => {
    fetchProductSizes.mockResolvedValue([])
    renderDrawer()
    await waitFor(() => expect(sizeSection()).toBeInTheDocument())

    fireEvent.change(sizeInput(), { target: { value: '9500' } })
    fireEvent.blur(sizeInput())

    await waitFor(() => expect(createProductSize).toHaveBeenCalledWith(
      10, { size: 'CHICA', price: 9500 }, 'tok',
    ))
  })

  test('con tamaño cargado, edita el precio en vez de crear otra fila', async () => {
    renderDrawer()
    await waitFor(() => expect(sizeInput()).toHaveValue(9000))

    fireEvent.change(sizeInput(), { target: { value: '9500' } })
    fireEvent.blur(sizeInput())

    await waitFor(() => expect(updateProductSize).toHaveBeenCalledWith(
      10, 50, { price: 9500 }, 'tok',
    ))
    expect(createProductSize).not.toHaveBeenCalled()
  })

  test('el toggle da de baja el tamaño sin borrarlo', async () => {
    renderDrawer()
    await waitFor(() => expect(sizeSection()).toBeInTheDocument())

    const section = sizeSection().closest('.pbc-section')
    fireEvent.click(within(section).getByRole('checkbox'))

    await waitFor(() => expect(updateProductSize).toHaveBeenCalledWith(
      10, 50, { active: false }, 'tok',
    ))
  })

  test('un tamaño inactivo se explica en vez de desaparecer', async () => {
    fetchProductSizes.mockResolvedValue([{ ...CHICA, active: false }])
    renderDrawer()

    await waitFor(() => expect(screen.getByText(/dado de baja/i)).toBeInTheDocument())
  })

  test('un MANAGER no puede editar el precio base del tamaño', async () => {
    useAuth.mockReturnValue({ token: 'tok', tenantId: 1, role: 'MANAGER', branchId: 1 })
    renderDrawer()

    await waitFor(() => expect(sizeInput()).toBeDisabled())
  })
})

describe('ProductDrawer · precio del tamaño por sucursal (US-SIZE-F-01)', () => {
  const sizeCell = branchName => screen.getByLabelText(`Precio chica en ${branchName}`)

  test('la columna de chica muestra el precio resuelto de cada sucursal', async () => {
    renderDrawer()

    // Centro tiene override 9900; Norte usa el precio base del tamaño (9000).
    await waitFor(() => expect(sizeCell('Centro')).toHaveValue(9900))
    expect(sizeCell('Norte')).toHaveValue(9000)
  })

  test('la columna no existe si no hay tamaño activo', async () => {
    fetchProductSizes.mockResolvedValue([])
    renderDrawer()

    await waitFor(() => expect(screen.getByLabelText('Precio en Centro')).toBeInTheDocument())
    expect(screen.queryByLabelText('Precio chica en Centro')).not.toBeInTheDocument()
  })

  test('guarda el override del tamaño para esa sucursal', async () => {
    renderDrawer()
    await waitFor(() => expect(sizeCell('Norte')).toHaveValue(9000))

    fireEvent.change(sizeCell('Norte'), { target: { value: '9700' } })
    fireEvent.blur(sizeCell('Norte'))

    await waitFor(() => expect(updateProductSizeBranchConfig).toHaveBeenCalledWith(
      10, 50, { branchId: 2, priceOverride: 9700 }, 'tok',
    ))
  })

  test('igualar el precio base del tamaño limpia el override', async () => {
    // Mismo criterio que el override de precio del producto: igualar la base = sin override.
    renderDrawer()
    await waitFor(() => expect(sizeCell('Centro')).toHaveValue(9900))

    fireEvent.change(sizeCell('Centro'), { target: { value: '9000' } })
    fireEvent.blur(sizeCell('Centro'))

    await waitFor(() => expect(updateProductSizeBranchConfig).toHaveBeenCalledWith(
      10, 50, { branchId: 1, priceOverride: null }, 'tok',
    ))
  })

  test('un precio inválido revierte al valor mostrado y no llama al backend', async () => {
    renderDrawer()
    await waitFor(() => expect(sizeCell('Centro')).toHaveValue(9900))

    fireEvent.change(sizeCell('Centro'), { target: { value: '0' } })
    fireEvent.blur(sizeCell('Centro'))

    await waitFor(() => expect(sizeCell('Centro')).toHaveValue(9900))
    expect(updateProductSizeBranchConfig).not.toHaveBeenCalled()
  })
})
