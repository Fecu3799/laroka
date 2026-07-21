import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
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

describe('MenuScreen — botón (+) con confirmación y contador', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(routedFetch))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  // El label cambia a "Agregar otro …" una vez que hay unidades en el carrito.
  const addBtn = () => screen.getByLabelText(/Agregar (otro )?Muzzarella/)

  it('confirma con un check y después muestra las unidades en el carrito', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderMenu()
    await screen.findByText('Muzzarella')

    fireEvent.click(addBtn())

    // Durante la confirmación el botón muestra el check con el pulso.
    expect(document.querySelector('.product-add-btn--added')).toBeInTheDocument()
    expect(document.querySelector('.product-add-qty')).toBeNull()

    await act(async () => { vi.advanceTimersByTime(700) })

    // Pasado el pulso queda el contador.
    expect(document.querySelector('.product-add-btn--added')).toBeNull()
    expect(document.querySelector('.product-add-qty').textContent).toBe('1')
  })

  it('seguir tocando suma unidades', async () => {
    renderMenu()
    await screen.findByText('Muzzarella')

    fireEvent.click(addBtn())
    fireEvent.click(addBtn())
    fireEvent.click(addBtn())

    await waitFor(() => {
      expect(screen.getByLabelText(/3 en el carrito/)).toBeInTheDocument()
    })
  })

  it('sin nada en el carrito el botón no muestra contador', async () => {
    renderMenu()
    await screen.findByText('Muzzarella')

    expect(document.querySelector('.product-add-qty')).toBeNull()
    expect(addBtn()).toHaveAccessibleName('Agregar Muzzarella')
  })
})

describe('MenuScreen — vuelta al menú desde el detalle', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(routedFetch))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const detail = () => document.querySelector('.detail-screen')

  // El detalle muestra "¡Agregado!" un segundo antes de cerrarse, más la animación de
  // salida: el waitFor por defecto (1s) se queda corto.
  const waitForClose = () =>
    waitFor(() => expect(detail()).not.toBeInTheDocument(), { timeout: 3000 })

  it('agregar desde el detalle confirma y después vuelve al menú', async () => {
    renderMenu()
    fireEvent.click(await screen.findByText('Muzzarella'))
    expect(detail()).toBeInTheDocument()

    fireEvent.click(screen.getByText('Agregar al carrito'))

    // Primero la confirmación, sin cerrar.
    expect(screen.getByText('¡Agregado!')).toBeInTheDocument()
    expect(detail()).toBeInTheDocument()

    await waitForClose()
  })

  it('el menú conserva la posición de scroll al volver del detalle', async () => {
    // El detalle es un overlay sobre el menú, que nunca se desmonta: por eso el scroll se
    // conserva solo, sin guardar ni restaurar nada. Este test lo fija como contrato.
    renderMenu()
    await screen.findByText('Muzzarella')
    const main = document.querySelector('.menu-main')
    main.scrollTop = 420

    fireEvent.click(screen.getByText('Muzzarella'))
    fireEvent.click(screen.getByText('Agregar al carrito'))
    await waitForClose()

    expect(document.querySelector('.menu-main')).toBe(main)
    expect(main.scrollTop).toBe(420)
  })
})
