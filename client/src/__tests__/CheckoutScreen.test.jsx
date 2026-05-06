import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CheckoutScreen } from '../components/CheckoutScreen'

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
