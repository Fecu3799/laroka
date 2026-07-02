import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MenuScreen } from '../pages/MenuScreen'

// getTenantProfile: sin perfil → no modal de bienvenida ni botón "Sobre nosotros".
vi.mock('../services/tenantService', () => ({
  getTenantProfile: vi.fn(() => Promise.resolve(null)),
}))

const MENU = [
  {
    categoryId: 1,
    categoryName: 'Pizzas',
    products: [
      { id: 1, name: 'Muzzarella', description: 'Clásica', price: 2800, imageUrl: null, available: true },
      { id: 2, name: 'Napolitana', description: 'Con tomate', price: 3200, imageUrl: null, available: false },
    ],
  },
]

// fetch ruteado por URL: el menú devuelve MENU; cualquier otra ruta (branch) {}.
function routedFetch(url) {
  if (String(url).includes('/menu')) {
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(MENU) })
  }
  return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) })
}

function renderMenu() {
  render(
    <MenuScreen branchId={1} branchName="Playa Unión" onChangeBranch={vi.fn()} />
  )
}

describe('MenuScreen — productos no disponibles (US-15-CF-05)', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(routedFetch))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('muestra el badge "No disponible" en el producto con available=false', async () => {
    renderMenu()
    expect(await screen.findByText('Napolitana')).toBeInTheDocument()
    expect(screen.getByText('No disponible')).toBeInTheDocument()
  })

  it('el precio sigue visible en el producto no disponible', async () => {
    renderMenu()
    await screen.findByText('Napolitana')
    expect(screen.getByText('$3.200')).toBeInTheDocument()
  })

  it('el producto disponible tiene botón de agregar y el no disponible no', async () => {
    renderMenu()
    await screen.findByText('Muzzarella')
    expect(screen.getByLabelText('Agregar Muzzarella')).toBeInTheDocument()
    expect(screen.queryByLabelText('Agregar Napolitana')).not.toBeInTheDocument()
  })

  it('el producto no disponible no es interactivo (aria-disabled, sin role button)', async () => {
    renderMenu()
    const napo = (await screen.findByText('Napolitana')).closest('li')
    expect(napo).toHaveAttribute('aria-disabled', 'true')
    expect(napo).not.toHaveAttribute('role', 'button')
  })

  it('tap sobre el no disponible no abre el detalle; sobre el disponible sí', async () => {
    renderMenu()
    const napo = (await screen.findByText('Napolitana')).closest('li')
    fireEvent.click(napo)
    // El detalle del producto expone el botón "Volver al menú".
    expect(screen.queryByLabelText('Volver al menú')).not.toBeInTheDocument()

    const muzza = screen.getByText('Muzzarella').closest('li')
    fireEvent.click(muzza)
    await waitFor(() =>
      expect(screen.getByLabelText('Volver al menú')).toBeInTheDocument()
    )
  })
})
