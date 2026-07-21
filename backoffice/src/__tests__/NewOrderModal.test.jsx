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

describe('NewOrderModal · tamaño chica (US-SIZE-F-03)', () => {
  // Muzzarella tiene tamaño chica cargado; Calabresa no. Gaseosa es de otra categoría.
  const MENU_SIZES = [
    {
      categoryName: 'Pizzas',
      allowsHalfAndHalf: true,
      allowsSizes: true,
      products: [
        {
          id: 10, name: 'Muzzarella', price: 15000, available: true,
          sizes: [{ id: 50, size: 'CHICA', price: 9000 }],
        },
        { id: 11, name: 'Calabresa', price: 17000, available: true, sizes: [] },
      ],
    },
    {
      categoryName: 'Bebidas',
      allowsHalfAndHalf: false,
      allowsSizes: false,
      products: [{ id: 30, name: 'Gaseosa', price: 1500, available: true, sizes: [] }],
    },
  ]

  const catalog = () => within(document.querySelector('.nom-col-left'))
  const row = name => catalog().getByText(name).closest('.nom-product-row')
  // Anclado al inicio: el aria-label del ½ deshabilitado también menciona "tamaño chica".
  const chicaBtn = name =>
    within(row(name)).queryByRole('button', {
      name: /^(elegir tamaño chica|volver a tamaño grande)/i,
    })
  const halfBtn = name =>
    within(row(name)).queryByRole('button', { name: /mitad y mitad/i })

  async function renderSizes() {
    fetchBranchMenu.mockResolvedValue(MENU_SIZES)
    render(<NewOrderModal open onClose={vi.fn()} />)
    await screen.findByText('Muzzarella')
  }

  test('el botón de chica aparece sólo donde hay tamaño cargado', async () => {
    await renderSizes()

    expect(chicaBtn('Muzzarella')).toBeInTheDocument()
    // Misma categoría pero sin tamaños cargados.
    expect(chicaBtn('Calabresa')).toBeNull()
    // Categoría que no admite tamaños.
    expect(chicaBtn('Gaseosa')).toBeNull()
  })

  test('marcar chica muestra el precio del tamaño en la fila', async () => {
    await renderSizes()
    expect(within(row('Muzzarella')).getByText('$15.000')).toBeInTheDocument()

    fireEvent.click(chicaBtn('Muzzarella'))

    expect(within(row('Muzzarella')).getByText('$9.000')).toBeInTheDocument()
  })

  test('marcar chica deshabilita mitad y mitad en esa fila, con el motivo', async () => {
    await renderSizes()
    expect(halfBtn('Muzzarella')).toBeEnabled()

    fireEvent.click(chicaBtn('Muzzarella'))

    expect(halfBtn('Muzzarella')).toBeDisabled()
    expect(halfBtn('Muzzarella')).toHaveAttribute(
      'title', 'Mitad y mitad sólo está disponible en tamaño grande',
    )
    // Otra pizza sin chica marcada sigue disponible para combinar.
    expect(halfBtn('Calabresa')).toBeEnabled()
  })

  test('desmarcar vuelve al tamaño grande y rehabilita mitad y mitad', async () => {
    await renderSizes()
    fireEvent.click(chicaBtn('Muzzarella'))
    fireEvent.click(chicaBtn('Muzzarella'))

    expect(halfBtn('Muzzarella')).toBeEnabled()
    expect(within(row('Muzzarella')).getByText('$15.000')).toBeInTheDocument()
  })

  test('el ítem entra al carrito con el nombre y el precio del tamaño', async () => {
    await renderSizes()
    fireEvent.click(chicaBtn('Muzzarella'))
    fireEvent.click(row('Muzzarella'))

    const cartItem = screen.getByText('Muzzarella (Chica)').closest('.nom-cart-item')
    expect(within(cartItem).getByText('$9.000')).toBeInTheDocument()
  })

  test('la chica no se fusiona con el mismo producto en tamaño grande', async () => {
    await renderSizes()
    fireEvent.click(row('Muzzarella'))          // entera
    fireEvent.click(chicaBtn('Muzzarella'))
    fireEvent.click(row('Muzzarella'))          // chica

    const cart = document.querySelectorAll('.nom-cart-item')
    expect(cart).toHaveLength(2)
    const names = [...cart].map(el => el.querySelector('.nom-cart-name').textContent)
    expect(names).toEqual(['Muzzarella', 'Muzzarella (Chica)'])
  })

  test('el pedido viaja con productSizeId en el ítem chica', async () => {
    await renderSizes()
    fireEvent.click(chicaBtn('Muzzarella'))
    fireEvent.click(row('Muzzarella'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalled())
    expect(createBackofficeOrder.mock.calls[0][0].items).toEqual([
      { productId: 10, productSizeId: 50, quantity: 1 },
    ])
  })

  test('el ítem en tamaño grande sigue viajando sin productSizeId', async () => {
    // El grande es implícito: la ausencia del campo, no un id.
    await renderSizes()
    fireEvent.click(row('Muzzarella'))
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))

    await waitFor(() => expect(createBackofficeOrder).toHaveBeenCalled())
    expect(createBackofficeOrder.mock.calls[0][0].items).toEqual([
      { productId: 10, quantity: 1 },
    ])
  })
})

describe('NewOrderModal · indicador de scroll en productos seleccionados', () => {
  // jsdom no hace layout: scrollHeight/clientHeight son siempre 0. Se stubean como propiedad
  // propia de HTMLElement.prototype (las nativas viven en Element.prototype, así que alcanza
  // con borrarlas para restaurar). `grow` simula que la lista se estira al agregar un ítem.
  function stubScrollMetrics({ scrollHeight, clientHeight }) {
    let height = scrollHeight
    Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
      configurable: true, get() { return height },
    })
    Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
      configurable: true, get() { return clientHeight },
    })
    return {
      grow(by) { height += by },
      restore() {
        delete HTMLElement.prototype.scrollHeight
        delete HTMLElement.prototype.clientHeight
      },
    }
  }

  const hint = () => document.querySelector('.nom-scroll-hint--bottom')
  const hintUp = () => document.querySelector('.nom-scroll-hint--top')
  const panel = () => document.querySelector('.nom-items-section')

  async function renderWithItems() {
    render(<NewOrderModal open onClose={vi.fn()} />)
    await screen.findByText('Muzzarella')
    fireEvent.click(screen.getByLabelText('Agregar Muzzarella'))
  }

  test('no aparece si la lista entra completa en el panel', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 200, clientHeight: 200 })
    await renderWithItems()

    expect(hint()).toBeNull()
    scroll.restore()
  })

  test('aparece cuando la lista excede el alto visible', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()

    await waitFor(() => expect(hint()).toBeInTheDocument())
    scroll.restore()
  })

  test('no aparece por un desborde de pocos píxeles sin ítem oculto', async () => {
    // El caso real que la volvía molesta: con la lista visualmente completa, el padding del
    // contenedor y el redondeo de las filas desbordan unos píxeles que no tapan nada.
    const scroll = stubScrollMetrics({ scrollHeight: 208, clientHeight: 200 })
    await renderWithItems()

    expect(hint()).toBeNull()
    scroll.restore()
  })

  test('aparece cuando lo oculto ya alcanza para tapar parte de un ítem', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 240, clientHeight: 200 })
    await renderWithItems()

    await waitFor(() => expect(hint()).toBeInTheDocument())
    scroll.restore()
  })

  test('desaparece al llegar al final de la lista', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    await waitFor(() => expect(hint()).toBeInTheDocument())

    // scrollTop + clientHeight === scrollHeight → no queda nada oculto.
    panel().scrollTop = 400
    fireEvent.scroll(panel())

    await waitFor(() => expect(hint()).toBeNull())
    scroll.restore()
  })

  test('la flecha es un botón que baja el panel', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    await waitFor(() => expect(hint()).toBeInTheDocument())
    expect(panel().scrollTop).toBe(0)

    fireEvent.click(screen.getByRole('button', { name: /siguiente producto/i }))

    // jsdom no calcula layout, así que el paso cae al fallback; lo que importa acá es que
    // el click baje el panel y no lo mande al final de un saque.
    expect(panel().scrollTop).toBeGreaterThan(0)
    expect(panel().scrollTop).toBeLessThan(400)
    scroll.restore()
  })

  test('bajando con la flecha, al llegar al final la flecha desaparece', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 232, clientHeight: 200 })
    await renderWithItems()
    await waitFor(() => expect(hint()).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /siguiente producto/i }))
    fireEvent.scroll(panel())

    await waitFor(() => expect(hint()).toBeNull())
    scroll.restore()
  })

  test('la flecha de subir no está mientras el panel está arriba de todo', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    await waitFor(() => expect(hint()).toBeInTheDocument())

    expect(hintUp()).toBeNull()
    scroll.restore()
  })

  test('la flecha de subir aparece al dejar contenido tapado arriba', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    panel().scrollTop = 200
    fireEvent.scroll(panel())

    // Con contenido tapado de los dos lados, conviven las dos flechas.
    await waitFor(() => expect(hintUp()).toBeInTheDocument())
    expect(hint()).toBeInTheDocument()
    scroll.restore()
  })

  test('la flecha de subir sube el panel y se va al llegar arriba de todo', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    panel().scrollTop = 32
    fireEvent.scroll(panel())
    await waitFor(() => expect(hintUp()).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /producto anterior/i }))
    fireEvent.scroll(panel())

    expect(panel().scrollTop).toBe(0)
    await waitFor(() => expect(hintUp()).toBeNull())
    scroll.restore()
  })

  test('reaparece al agregar un ítem que vuelve a exceder el alto', async () => {
    const scroll = stubScrollMetrics({ scrollHeight: 600, clientHeight: 200 })
    await renderWithItems()
    panel().scrollTop = 400
    fireEvent.scroll(panel())
    await waitFor(() => expect(hint()).toBeNull())

    // El ítem nuevo estira la lista: vuelve a haber contenido por debajo del viewport.
    scroll.grow(100)
    fireEvent.click(screen.getByLabelText('Agregar Napolitana'))

    await waitFor(() => expect(hint()).toBeInTheDocument())
    scroll.restore()
  })
})
