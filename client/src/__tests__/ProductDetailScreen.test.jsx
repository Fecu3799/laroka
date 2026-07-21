import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { ProductDetailScreen } from '../pages/ProductDetailScreen'

const MUZZA = {
  id: 1, name: 'Muzzarella', description: 'Clásica', price: 2800,
  imageUrl: null, available: true, categoryName: 'Pizzas',
}
const NAPO = { id: 2, name: 'Napolitana', price: 3200, imageUrl: null, available: true }
const FUGAZZETA = { id: 3, name: 'Fugazzeta', price: 2500, imageUrl: null, available: true }

function renderDetail(overrides = {}, handlers = {}) {
  const props = {
    product: { ...MUZZA, ...overrides },
    onBack: vi.fn(),
    onAddToCart: vi.fn(),
    onAddHalfAndHalf: vi.fn(),
    onAddSized: vi.fn(),
    ...handlers,
  }
  render(<ProductDetailScreen {...props} />)
  return props
}

const halfRow = () => screen.queryByRole('button', { name: /pedir mitad y mitad/i })
const cta = () => screen.getByRole('button', { name: /agregar al carrito|elegí la otra mitad/i })
const totalPrice = () => screen.getByTestId('detail-total-price')

describe('ProductDetailScreen — opción mitad y mitad (US-HH-F-01)', () => {
  it('no ofrece la opción si la categoría no la permite', () => {
    renderDetail({ allowsHalfAndHalf: false, halfAndHalfCandidates: [NAPO] })

    expect(halfRow()).not.toBeInTheDocument()
  })

  it('no ofrece la opción si no hay otro producto disponible con el que combinar', () => {
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [] })

    expect(halfRow()).not.toBeInTheDocument()
  })

  it('la opción convive con el flujo normal de agregar el producto entero', () => {
    // No lo reemplaza: el acordeón arranca colapsado y el CTA sigue agregando el entero.
    const props = renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })

    expect(halfRow()).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(cta())

    expect(props.onAddToCart).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }), 1)
    expect(props.onAddHalfAndHalf).not.toHaveBeenCalled()
  })

  it('los candidatos aparecen recién al expandir la fila', () => {
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO, FUGAZZETA] })

    expect(screen.queryByRole('radio', { name: /Napolitana/ })).not.toBeInTheDocument()

    fireEvent.click(halfRow())

    expect(screen.getByRole('radio', { name: /Napolitana/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Fugazzeta/ })).toBeInTheDocument()
  })

  it('el precio pasa a ser el de la mitad más cara al elegir la otra mitad', () => {
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })

    expect(totalPrice()).toHaveTextContent('$2.800')

    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))

    // Muzzarella 2800 + Napolitana 3200 → 3200.
    expect(totalPrice()).toHaveTextContent('$3.200')
  })

  it('el preview conserva el precio del producto base cuando es el más caro', () => {
    // Muzzarella 2800 + Fugazzeta 2500 → 2800.
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [FUGAZZETA] })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Fugazzeta/ }))

    expect(totalPrice()).toHaveTextContent('$2.800')
  })

  it('el precio de la combinación se multiplica por la cantidad', () => {
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    fireEvent.click(screen.getByRole('button', { name: /aumentar cantidad/i }))

    expect(totalPrice()).toHaveTextContent('$6.400')
  })

  it('bloquea el CTA mientras falta elegir la otra mitad', () => {
    // Sin esto, el CTA agregaría el producto entero pese al acordeón abierto.
    const props = renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())

    expect(cta()).toBeDisabled()
    fireEvent.click(cta())
    expect(props.onAddToCart).not.toHaveBeenCalled()
    expect(props.onAddHalfAndHalf).not.toHaveBeenCalled()
  })

  it('al confirmar entrega ambos productos y la cantidad elegida', () => {
    const props = renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    // La cantidad se ajusta después de armar la combinación: expandir y elegir la mitad
    // reinician el stepper.
    fireEvent.click(screen.getByRole('button', { name: /aumentar cantidad/i }))
    fireEvent.click(cta())

    expect(props.onAddHalfAndHalf).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1 }),
      expect.objectContaining({ id: 2 }),
      2,
    )
    // El alta del combo no dispara además el alta del producto entero.
    expect(props.onAddToCart).not.toHaveBeenCalled()
  })

  it('al colapsar oculta la lista, limpia la selección y vuelve al precio del entero', () => {
    renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    fireEvent.click(halfRow())

    expect(screen.queryByRole('radio', { name: /Napolitana/ })).not.toBeInTheDocument()
    expect(totalPrice()).toHaveTextContent('$2.800')

    // Volver a expandirlo no debe arrastrar la mitad elegida antes.
    fireEvent.click(halfRow())
    expect(screen.getByRole('radio', { name: /Napolitana/ })).not.toBeChecked()
    expect(cta()).toBeDisabled()
  })
})

describe('ProductDetailScreen — tamaños (US-SIZE-F-02)', () => {
  const CHICA = { id: 50, size: 'CHICA', price: 1900 }

  function renderConTamanios(overrides = {}) {
    return renderDetail({
      allowsSizes: true,
      sizes: [CHICA],
      allowsHalfAndHalf: true,
      halfAndHalfCandidates: [NAPO],
      ...overrides,
    })
  }

  const sizeRadio = name => screen.queryByRole('radio', { name: new RegExp(name) })

  it('no ofrece tamaños si la categoría no los habilita', () => {
    renderConTamanios({ allowsSizes: false })

    expect(sizeRadio('Grande')).not.toBeInTheDocument()
    expect(sizeRadio('Chica')).not.toBeInTheDocument()
  })

  it('sin tamaños cargados la sección no desaparece: queda fija en Grande', () => {
    // Si se ocultara, el cliente no se enteraría de que existen tamaños y leería este
    // producto como si nunca hubiera opción. Se muestra elegido pero no se puede cambiar.
    renderConTamanios({ sizes: [] })

    expect(sizeRadio('Grande')).toBeInTheDocument()
    expect(sizeRadio('Grande')).toBeChecked()
    expect(sizeRadio('Grande')).toBeDisabled()
    expect(sizeRadio('Chica')).not.toBeInTheDocument()
  })

  it('sin tamaños cargados explica que sólo va en grande', () => {
    renderConTamanios({ sizes: [] })

    expect(screen.getByText(/sólo en tamaño grande/i)).toBeInTheDocument()
  })

  it('sin tamaños cargados, mitad y mitad sigue disponible', () => {
    // El bloqueo de mitad y mitad depende de haber elegido un tamaño alternativo, no de que
    // el producto tenga tamaños cargados: en grande siempre se puede combinar.
    renderConTamanios({ sizes: [] })

    expect(halfRow()).toBeEnabled()
    expect(screen.queryByText(/sólo disponible en tamaño grande/i)).not.toBeInTheDocument()
  })

  it('sin tamaños cargados el ítem se agrega sin productSizeId', () => {
    const props = renderConTamanios({ sizes: [] })
    fireEvent.click(cta())

    expect(props.onAddToCart).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }), 1)
    expect(props.onAddSized).not.toHaveBeenCalled()
  })

  it('ofrece Grande (por defecto) y Chica con sus precios', () => {
    renderConTamanios()

    expect(sizeRadio('Grande')).toBeChecked()
    expect(sizeRadio('Chica')).not.toBeChecked()
    // El precio de cada tamaño viaja en el nombre accesible de su propio radio.
    expect(sizeRadio('Grande')).toHaveAccessibleName(expect.stringContaining('$2.800'))
    expect(sizeRadio('Chica')).toHaveAccessibleName(expect.stringContaining('$1.900'))
  })

  it('elegir Chica cambia el precio al del tamaño', () => {
    renderConTamanios()
    expect(totalPrice()).toHaveTextContent('$2.800')

    fireEvent.click(sizeRadio('Chica'))

    expect(totalPrice()).toHaveTextContent('$1.900')
  })

  it('con Chica elegida, mitad y mitad queda deshabilitada pero visible, con el motivo', () => {
    renderConTamanios()
    fireEvent.click(sizeRadio('Chica'))

    const accordion = halfRow()
    expect(accordion).toBeInTheDocument()      // no se oculta
    expect(accordion).toBeDisabled()
    expect(screen.getByText(/sólo disponible en tamaño grande/i)).toBeInTheDocument()
  })

  it('volver a Grande vuelve a habilitar mitad y mitad', () => {
    renderConTamanios()
    fireEvent.click(sizeRadio('Chica'))
    fireEvent.click(sizeRadio('Grande'))

    expect(halfRow()).toBeEnabled()
    expect(screen.queryByText(/sólo disponible en tamaño grande/i)).not.toBeInTheDocument()
  })

  it('pasar a Chica descarta una combinación a medio armar', () => {
    renderConTamanios()
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    expect(totalPrice()).toHaveTextContent('$3.200')

    fireEvent.click(sizeRadio('Chica'))

    // El acordeón se cierra y el precio pasa a ser el del tamaño, no el de la combinación.
    expect(screen.queryByRole('radio', { name: /Napolitana/ })).not.toBeInTheDocument()
    expect(totalPrice()).toHaveTextContent('$1.900')
  })

  it('agrega el ítem con el tamaño elegido', () => {
    const props = renderConTamanios()
    fireEvent.click(sizeRadio('Chica'))
    fireEvent.click(screen.getByRole('button', { name: /aumentar cantidad/i }))
    fireEvent.click(cta())

    expect(props.onAddSized).toHaveBeenCalledWith(
      expect.objectContaining({ id: 1 }),
      expect.objectContaining({ id: 50, size: 'CHICA' }),
      2,
    )
    expect(props.onAddToCart).not.toHaveBeenCalled()
  })

  it('con Grande agrega el producto sin tamaño (comportamiento actual)', () => {
    // "Grande" no es un productSizeId: es la ausencia de tamaño (US-SIZE-03 rechaza
    // combinar tamaño con mitad y mitad, y grande debe seguir admitiéndola).
    const props = renderConTamanios()
    fireEvent.click(cta())

    expect(props.onAddToCart).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }), 1)
    expect(props.onAddSized).not.toHaveBeenCalled()
  })
})

describe('ProductDetailScreen — confirmación y vuelta al menú', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const advanceToClose = async () => {
    await act(async () => { vi.advanceTimersByTime(1000) })
  }

  it('muestra "¡Agregado!" y recién después de un segundo vuelve al menú', async () => {
    const props = renderDetail()
    fireEvent.click(cta())

    expect(props.onAddToCart).toHaveBeenCalled()
    // La confirmación queda visible: el detalle todavía no se cerró.
    expect(screen.getByText('¡Agregado!')).toBeInTheDocument()
    expect(props.onBack).not.toHaveBeenCalled()

    await advanceToClose()

    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('cierra el detalle al agregar una combinación', async () => {
    const props = renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    fireEvent.click(cta())

    expect(props.onAddHalfAndHalf).toHaveBeenCalled()
    await advanceToClose()

    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('cierra el detalle al agregar con tamaño', async () => {
    const props = renderDetail({
      allowsSizes: true,
      sizes: [{ id: 50, size: 'CHICA', price: 1900 }],
    })
    fireEvent.click(screen.getByRole('radio', { name: /Chica/ }))
    fireEvent.click(cta())

    expect(props.onAddSized).toHaveBeenCalled()
    await advanceToClose()

    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('tocar de nuevo durante la confirmación no agrega dos veces', async () => {
    // El botón sigue en pantalla ese segundo: sin guarda, un segundo toque duplicaba el ítem.
    const props = renderDetail()
    // Se guarda el nodo: tras el primer toque su nombre accesible pasa a "¡Agregado!".
    const button = cta()
    fireEvent.click(button)
    fireEvent.click(button)

    expect(props.onAddToCart).toHaveBeenCalledTimes(1)
    await advanceToClose()
    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('con el CTA bloqueado no agrega, no confirma ni cierra', async () => {
    // Mitad y mitad abierta sin elegir la otra mitad: el tap no debe hacer nada.
    const props = renderDetail({ allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] })
    fireEvent.click(halfRow())
    fireEvent.click(cta())

    expect(props.onAddHalfAndHalf).not.toHaveBeenCalled()
    expect(screen.queryByText('¡Agregado!')).not.toBeInTheDocument()
    await advanceToClose()
    expect(props.onBack).not.toHaveBeenCalled()
  })
})

describe('ProductDetailScreen — gestión de cantidad desde el detalle', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const CHICA = { id: 50, size: 'CHICA', price: 1900 }
  const qtyValue = () => document.querySelector('.detail-qty-number').textContent
  const minus = () => screen.getByRole('button', { name: /reducir cantidad/i })
  const plus = () => screen.getByRole('button', { name: /aumentar cantidad/i })
  const ctaText = () => document.querySelector('.detail-cta-label').textContent

  // Carrito simulado: sólo hace falta responder cuántas unidades tiene cada línea.
  function renderConCarrito(cart, overrides = {}) {
    const props = {
      product: { ...MUZZA, ...overrides },
      onBack: vi.fn(),
      onAddToCart: vi.fn(),
      onAddHalfAndHalf: vi.fn(),
      onAddSized: vi.fn(),
      onUpdateCartQty: vi.fn(),
      onRemoveFromCart: vi.fn(),
      getCartQty: id => cart[id] ?? 0,
    }
    render(<ProductDetailScreen {...props} />)
    return props
  }

  it('caso 1: sin unidades arranca en 1 y ofrece agregar', () => {
    renderConCarrito({})

    expect(qtyValue()).toBe('01')
    expect(ctaText()).toBe('Agregar al carrito')
    // Sin línea previa no se puede bajar de 1: "agregar 0" no significa nada.
    expect(minus()).toBeDisabled()
  })

  it('caso 2: con unidades arranca en esa cantidad y ofrece confirmar el cambio', () => {
    renderConCarrito({ 1: 3 })

    expect(qtyValue()).toBe('03')
    expect(ctaText()).toBe('Confirmar cambio')
  })

  it('caso 2: ajustar y confirmar actualiza la línea, sin agregar una nueva', async () => {
    const props = renderConCarrito({ 1: 3 })
    fireEvent.click(plus())

    expect(ctaText()).toBe('Confirmar cambio')
    fireEvent.click(screen.getByRole('button', { name: /confirmar cambio/i }))

    expect(props.onUpdateCartQty).toHaveBeenCalledWith(1, 4)
    expect(props.onAddToCart).not.toHaveBeenCalled()
    expect(screen.getByText('¡Actualizado!')).toBeInTheDocument()

    await act(async () => { vi.advanceTimersByTime(1000) })
    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('caso 2: bajar a 0 cambia el botón a quitar y remueve la línea', async () => {
    const props = renderConCarrito({ 1: 2 })
    fireEvent.click(minus())
    expect(ctaText()).toBe('Confirmar cambio')

    fireEvent.click(minus())

    expect(qtyValue()).toBe('00')
    expect(ctaText()).toBe('Quitar del carrito')
    // Nada se aplicó todavía: el cambio es diferido.
    expect(props.onRemoveFromCart).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /quitar del carrito/i }))

    expect(props.onRemoveFromCart).toHaveBeenCalledWith(1)
    expect(screen.getByText('¡Quitado!')).toBeInTheDocument()
    await act(async () => { vi.advanceTimersByTime(1000) })
    expect(props.onBack).toHaveBeenCalledTimes(1)
  })

  it('no baja de 0 aunque se siga tocando', () => {
    renderConCarrito({ 1: 1 })
    fireEvent.click(minus())

    expect(qtyValue()).toBe('00')
    expect(minus()).toBeDisabled()
  })

  it('al cambiar de variante recalcula la cantidad de esa otra línea', () => {
    // Grande tiene 2 unidades, Chica tiene 5.
    const cart = { 1: 2, 'size-1-50': 5 }
    renderConCarrito(cart, { allowsSizes: true, sizes: [CHICA] })

    expect(qtyValue()).toBe('02')

    fireEvent.click(screen.getByRole('radio', { name: /Chica/ }))
    expect(qtyValue()).toBe('05')

    fireEvent.click(screen.getByRole('radio', { name: /Grande/ }))
    expect(qtyValue()).toBe('02')
  })

  it('cambiar a una variante que no está en el carrito vuelve a arrancar en 1', () => {
    renderConCarrito({ 1: 2 }, { allowsSizes: true, sizes: [CHICA] })

    fireEvent.click(screen.getByRole('radio', { name: /Chica/ }))

    expect(qtyValue()).toBe('01')
    expect(ctaText()).toBe('Agregar al carrito')
    expect(minus()).toBeDisabled()
  })

  it('confirmar sobre la variante con tamaño actualiza esa línea, no la del entero', () => {
    const props = renderConCarrito(
      { 1: 2, 'size-1-50': 5 },
      { allowsSizes: true, sizes: [CHICA] },
    )
    fireEvent.click(screen.getByRole('radio', { name: /Chica/ }))
    fireEvent.click(plus())
    fireEvent.click(screen.getByRole('button', { name: /confirmar cambio/i }))

    expect(props.onUpdateCartQty).toHaveBeenCalledWith('size-1-50', 6)
  })

  it('mitad y mitad no se ata al carrito: siempre es un alta', () => {
    // Aunque el producto entero ya tenga unidades, la combinación es una línea nueva.
    const props = renderConCarrito(
      { 1: 4 },
      { allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO] },
    )
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))

    expect(ctaText()).toBe('Agregar al carrito')
    fireEvent.click(screen.getByRole('button', { name: /agregar al carrito/i }))

    expect(props.onAddHalfAndHalf).toHaveBeenCalled()
    expect(props.onUpdateCartQty).not.toHaveBeenCalled()
    expect(screen.getByText('¡Agregado!')).toBeInTheDocument()
  })
})

describe('ProductDetailScreen — mitad y mitad reinicia el stepper', () => {
  const qtyValue = () => document.querySelector('.detail-qty-number').textContent
  const ctaText = () => document.querySelector('.detail-cta-label').textContent

  function renderConCarrito(cart, overrides = {}) {
    const props = {
      product: { ...MUZZA, allowsHalfAndHalf: true, halfAndHalfCandidates: [NAPO], ...overrides },
      onBack: vi.fn(),
      onAddToCart: vi.fn(),
      onAddHalfAndHalf: vi.fn(),
      onAddSized: vi.fn(),
      onUpdateCartQty: vi.fn(),
      onRemoveFromCart: vi.fn(),
      getCartQty: id => cart[id] ?? 0,
    }
    render(<ProductDetailScreen {...props} />)
    return props
  }

  it('expandir la sección reinicia el stepper aunque la variante tenga unidades', () => {
    renderConCarrito({ 1: 4 })
    expect(qtyValue()).toBe('04')
    expect(ctaText()).toBe('Confirmar cambio')

    fireEvent.click(halfRow())

    // Armar una combinación es un alta nueva: no arrastra las unidades del entero.
    expect(qtyValue()).toBe('01')
    expect(ctaText()).toBe('Elegí la otra mitad')
  })

  it('elegir la otra mitad deja el stepper en el estado de alta nueva', () => {
    renderConCarrito({ 1: 4 })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))

    expect(qtyValue()).toBe('01')
    expect(ctaText()).toBe('Agregar al carrito')
  })

  it('colapsar sin confirmar devuelve el stepper al estado real del carrito', () => {
    renderConCarrito({ 1: 4 })
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
    expect(qtyValue()).toBe('01')

    fireEvent.click(halfRow())

    expect(qtyValue()).toBe('04')
    expect(ctaText()).toBe('Confirmar cambio')
  })

  it('colapsar con la variante fuera del carrito vuelve a 1 y a agregar', () => {
    renderConCarrito({})
    fireEvent.click(halfRow())
    fireEvent.click(halfRow())

    expect(qtyValue()).toBe('01')
    expect(ctaText()).toBe('Agregar al carrito')
  })

  it('con la sección abierta el mínimo del stepper vuelve a ser 1', () => {
    // Con 4 unidades el mínimo era 0 (para poder quitar); en modo combinación no.
    renderConCarrito({ 1: 4 })
    fireEvent.click(halfRow())

    expect(screen.getByRole('button', { name: /reducir cantidad/i })).toBeDisabled()
  })
})
