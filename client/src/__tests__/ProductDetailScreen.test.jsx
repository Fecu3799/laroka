import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
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
    fireEvent.click(screen.getByRole('button', { name: /aumentar cantidad/i }))
    fireEvent.click(halfRow())
    fireEvent.click(screen.getByRole('radio', { name: /Napolitana/ }))
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
