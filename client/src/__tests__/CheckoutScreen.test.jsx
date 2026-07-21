import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CheckoutScreen } from '../pages/CheckoutScreen'

vi.mock('../hooks/usePreferredBranch', () => ({
  usePreferredBranch: () => ({ deliveryFee: 500, serviceFee: 100 }),
}))

const ITEMS = [{ id: 1, name: 'Muzarella', price: 2000, qty: 1 }]

function renderCheckout(props = {}) {
  const onBack = vi.fn()
  const onConfirm = vi.fn()
  render(<CheckoutScreen onBack={onBack} onConfirm={onConfirm} items={ITEMS} {...props} />)
  return { onBack, onConfirm }
}

describe('CheckoutScreen — validation', () => {
  it('CTA button is disabled while form is empty', () => {
    renderCheckout()
    const btn = screen.getByRole('button', { name: /confirmar pedido/i })
    expect(btn).toHaveStyle({ pointerEvents: 'none' })
  })

  it('shows error when nombre is empty on submit', async () => {
    const user = userEvent.setup()
    renderCheckout()
    await user.type(screen.getByPlaceholderText('11 0000-0000'), '1122334455')
    await user.type(screen.getByPlaceholderText('Calle y número, piso, depto'), 'Av. San Martín 123')
    // CTA has pointer-events:none — bypass with fireEvent
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    await waitFor(() => {
      expect(screen.getByText('Ingresá tu nombre')).toBeInTheDocument()
    })
  })

  it('shows error when telefono is empty on submit', async () => {
    const user = userEvent.setup()
    renderCheckout()
    await user.type(screen.getByPlaceholderText('¿Cómo te llamás?'), 'Juan')
    await user.type(screen.getByPlaceholderText('Calle y número, piso, depto'), 'Av. San Martín 123')
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    await waitFor(() => {
      expect(screen.getByText('Ingresá tu teléfono')).toBeInTheDocument()
    })
  })

  it('shows address error for DELIVERY with empty address', async () => {
    const user = userEvent.setup()
    renderCheckout()
    await user.type(screen.getByPlaceholderText('¿Cómo te llamás?'), 'Juan')
    await user.type(screen.getByPlaceholderText('11 0000-0000'), '1122334455')
    fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    await waitFor(() => {
      expect(screen.getByText('Ingresá la dirección de entrega')).toBeInTheDocument()
    })
  })

  it('no address error for TAKEAWAY even with empty address', async () => {
    const user = userEvent.setup()
    renderCheckout()
    await user.click(screen.getByRole('button', { name: /retirar/i }))
    await user.type(screen.getByPlaceholderText('¿Cómo te llamás?'), 'Juan')
    await user.type(screen.getByPlaceholderText('11 0000-0000'), '1122334455')
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    })
    expect(screen.queryByText('Ingresá la dirección de entrega')).not.toBeInTheDocument()
  })

  it('calls onConfirm with correct data when form is valid (TAKEAWAY)', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderCheckout()
    await user.click(screen.getByRole('button', { name: /retirar/i }))
    await user.type(screen.getByPlaceholderText('¿Cómo te llamás?'), 'Juan')
    await user.type(screen.getByPlaceholderText('11 0000-0000'), '1122334455')
    await user.click(screen.getByRole('button', { name: /confirmar pedido/i }))
    await waitFor(() => {
      expect(onConfirm).toHaveBeenCalledWith(
        expect.objectContaining({ orderType: 'takeaway', nombre: 'Juan', telefono: '1122334455' })
      )
    })
  })

  it('shows correct CTA label when MercadoPago is selected', async () => {
    const user = userEvent.setup()
    renderCheckout()
    await user.click(screen.getByRole('button', { name: /mercadopago/i }))
    expect(screen.getByRole('button', { name: /ir a pagar/i })).toBeInTheDocument()
  })
})

describe('CheckoutScreen — ítem mitad y mitad (US-HH-F-02)', () => {
  const COMBO = {
    id: 'hh-1-4', productId: 1, secondProductId: 4,
    productName: 'Muzzarella', secondProductName: 'Calabresa',
    name: '½ Muzzarella + ½ Calabresa', price: 3400, qty: 1,
  }

  it('el resumen muestra la combinación con el nombre de las dos mitades', () => {
    renderCheckout({ items: [COMBO] })
    fireEvent.click(screen.getByText('Resumen del pedido'))

    expect(screen.getByText('1× ½ Muzzarella + ½ Calabresa')).toBeInTheDocument()
  })

  it('el precio del ítem es el resuelto al armarlo, no un cálculo del checkout', () => {
    // 3400 es el mayor de las dos mitades, ya resuelto en el detalle de producto con la
    // misma regla del backend. El checkout sólo lo multiplica por la cantidad.
    renderCheckout({ items: [{ ...COMBO, qty: 2 }] })
    fireEvent.click(screen.getByText('Resumen del pedido'))

    expect(screen.getByText('2× ½ Muzzarella + ½ Calabresa')).toBeInTheDocument()
    expect(screen.getAllByText('$6.800').length).toBeGreaterThan(0)
  })

  it('el subtotal suma la combinación junto a los ítems simples', () => {
    renderCheckout({ items: [COMBO, { id: 7, name: 'Fugazzeta', price: 2500, qty: 2 }] })
    fireEvent.click(screen.getByText('Resumen del pedido'))

    // 3400 + (2500 × 2) = 8400
    expect(screen.getAllByText('$8.400').length).toBeGreaterThan(0)
  })
})
