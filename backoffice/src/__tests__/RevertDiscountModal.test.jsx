import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import RevertDiscountModal from '../components/RevertDiscountModal'
import { revertDiscount } from '../services/ordersService'

vi.mock('../services/ordersService', () => ({
  revertDiscount: vi.fn().mockResolvedValue(undefined),
}))

// Subtotal 1000 + envío 500 + servicio 200 = 1700; con 10% descontado el total es 1600.
const ORDER = {
  id: 'aaaa0000-0000-0000-0000-000000000001',
  subtotal: 1000,
  deliveryFee: 500,
  serviceFee: 200,
  totalAmount: 1600,
  discount: { percentage: 10, discountAmount: 100, reason: 'CUSTOMER_PROMO' },
}

function renderModal(order = ORDER) {
  const onClose = vi.fn()
  const onReverted = vi.fn()
  render(
    <RevertDiscountModal
      order={order}
      token="test-token"
      branchId={1}
      onClose={onClose}
      onReverted={onReverted}
    />,
  )
  return { onClose, onReverted }
}

const confirmBtn = () => screen.getByRole('button', { name: /Borrar descuento/ })

beforeEach(() => {
  vi.clearAllMocks()
})

describe('RevertDiscountModal (US-19-06)', () => {
  test('muestra a cuánto vuelve el pedido (subtotal + fees, sin descuento)', () => {
    renderModal()
    // 1000 + 500 + 200 = 1700, no el total descontado (1600).
    expect(screen.getByText(/\$1\.700/)).toBeInTheDocument()
  })

  test('confirma reversión con motivo y nota, y avisa al cerrar', async () => {
    const { onClose, onReverted } = renderModal()
    fireEvent.change(screen.getByLabelText('Nota (opcional)'), {
      target: { value: 'se cargó por error' },
    })
    fireEvent.click(confirmBtn())

    await waitFor(() => expect(revertDiscount).toHaveBeenCalled())
    expect(revertDiscount).toHaveBeenCalledWith(
      ORDER.id,
      { reason: 'OTHER', note: 'se cargó por error' },
      'test-token',
      1,
    )
    await waitFor(() => expect(onReverted).toHaveBeenCalled())
    expect(onClose).toHaveBeenCalled()
  })

  test('el motivo por defecto es OTHER pero puede cambiarse al enum del backend', async () => {
    renderModal()
    // Abre el dropdown por su valor actual y elige otro; viaja la constante en inglés.
    fireEvent.click(screen.getByText('Otro'))
    fireEvent.click(screen.getByRole('button', { name: 'Promoción al cliente' }))
    fireEvent.click(confirmBtn())

    await waitFor(() => expect(revertDiscount).toHaveBeenCalled())
    expect(revertDiscount.mock.calls[0][1].reason).toBe('CUSTOMER_PROMO')
  })

  test('un error del backend se muestra inline y no cierra el modal', async () => {
    const err = new Error('No se puede modificar el descuento de un pedido pagado por MercadoPago o QR')
    err.status = 422
    revertDiscount.mockRejectedValueOnce(err)

    const { onClose, onReverted } = renderModal()
    fireEvent.click(confirmBtn())

    expect(await screen.findByRole('alert')).toHaveTextContent('MercadoPago o QR')
    expect(onReverted).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })
})
