import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import DiscountModal from '../components/DiscountModal'
import { canApplyDiscount, discountErrorMessage } from '../utils/ordersUtils'
import { applyDiscount } from '../services/ordersService'

vi.mock('../services/ordersService', () => ({
  applyDiscount: vi.fn().mockResolvedValue(undefined),
}))

// Subtotal 1000 + envío 500 + servicio 200 = 1700, la misma composición que usa
// el backend como base del descuento.
const ORDER = {
  id: 'aaaa0000-0000-0000-0000-000000000001',
  status: 'RECEIVED',
  paymentMethod: 'CASH',
  paymentStatus: 'PENDING',
  subtotal: 1000,
  deliveryFee: 500,
  serviceFee: 200,
  totalAmount: 1700,
}

function renderModal(order = ORDER) {
  const onClose = vi.fn()
  const onApplied = vi.fn()
  render(
    <DiscountModal
      order={order}
      token="test-token"
      branchId={1}
      onClose={onClose}
      onApplied={onApplied}
    />,
  )
  return { onClose, onApplied }
}

const percentageInput = () => screen.getByLabelText('Porcentaje')
const submitBtn = () => screen.getByRole('button', { name: /Aplicar descuento/ })
const discountAmount = () => screen.getByTestId('dsm-discount-amount').textContent
const finalTotal = () => screen.getByTestId('dsm-final-total').textContent

beforeEach(() => {
  vi.clearAllMocks()
})

// ── Preview en tiempo real ────────────────────────────────────

describe('preview del monto final', () => {
  test('calcula el descuento sobre el subtotal y deja los fees enteros', () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '10' } })

    // 10% de 1000 = 100; el total baja de 1700 a 1600 (envío y servicio intactos).
    expect(discountAmount()).toContain('100')
    expect(finalTotal()).toContain('1.600')
  })

  test('redondea HALF_UP a 2 decimales igual que el backend', () => {
    renderModal({ ...ORDER, subtotal: 1333.33, totalAmount: 2033.33 })
    fireEvent.change(percentageInput(), { target: { value: '15' } })

    // 1333.33 * 15% = 199.9995 → 200
    expect(discountAmount()).toContain('200')
  })

  test('0% deja el total sin cambios', () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '0' } })

    expect(discountAmount()).toContain('0')
    expect(finalTotal()).toContain('1.700')
  })

  test('100% descuenta el subtotal entero y deja solo los fees', () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '100' } })

    expect(discountAmount()).toContain('1.000')
    expect(finalTotal()).toContain('700')
  })
})

// ── Validación del porcentaje antes de enviar ─────────────────

describe('validación del porcentaje', () => {
  test('el submit arranca deshabilitado con el campo vacío', () => {
    renderModal()
    expect(submitBtn()).toBeDisabled()
  })

  test('un porcentaje fuera de rango deshabilita el submit y avisa', () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '150' } })

    expect(submitBtn()).toBeDisabled()
    expect(screen.getByText('Ingresá un número entre 0 y 100.')).toBeInTheDocument()
    expect(applyDiscount).not.toHaveBeenCalled()
  })

  test('un porcentaje negativo también se rechaza', () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '-5' } })

    expect(submitBtn()).toBeDisabled()
  })
})

// ── Envío ─────────────────────────────────────────────────────

describe('envío del descuento', () => {
  test('manda porcentaje, motivo y nota, y avisa al cerrar', async () => {
    const { onClose, onApplied } = renderModal()
    fireEvent.change(percentageInput(), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Nota (opcional)'), {
      target: { value: 'cortesía' },
    })
    fireEvent.click(submitBtn())

    await waitFor(() => expect(applyDiscount).toHaveBeenCalled())
    expect(applyDiscount).toHaveBeenCalledWith(
      ORDER.id,
      { percentage: 10, reason: 'CUSTOMER_PROMO', note: 'cortesía' },
      'test-token',
      1,
    )
    await waitFor(() => expect(onApplied).toHaveBeenCalled())
    expect(onClose).toHaveBeenCalled()
  })

  test('el dropdown de motivo manda el valor de enum del backend, no la etiqueta', async () => {
    renderModal()
    fireEvent.change(percentageInput(), { target: { value: '10' } })

    // La etiqueta es en español; el value que viaja es la constante en inglés.
    fireEvent.click(screen.getByText('Promoción al cliente'))
    // La opción clickeable es el <button> dentro del <li role="option">.
    fireEvent.click(screen.getByRole('button', { name: 'Ajuste por transferencia' }))
    fireEvent.click(submitBtn())

    await waitFor(() => expect(applyDiscount).toHaveBeenCalled())
    expect(applyDiscount.mock.calls[0][1].reason).toBe('TRANSFER_ADJUSTMENT')
  })
})

// ── Manejo de errores diferenciado 400 / 422 ──────────────────

describe('mensajes de error por código de estado', () => {
  test('400 explica el rango del porcentaje, no "Validation failed"', async () => {
    const err = new Error('Validation failed')
    err.status = 400
    applyDiscount.mockRejectedValueOnce(err)

    const { onClose } = renderModal()
    fireEvent.change(percentageInput(), { target: { value: '10' } })
    fireEvent.click(submitBtn())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El porcentaje debe ser un número entre 0 y 100.',
    )
    expect(screen.queryByText('Validation failed')).not.toBeInTheDocument()
    // El modal queda abierto para que el operador corrija.
    expect(onClose).not.toHaveBeenCalled()
  })

  test('422 muestra el guard de negocio del backend, distinto del de 400', async () => {
    const err = new Error(
      'No se puede aplicar un descuento a un pedido pagado por MercadoPago o QR',
    )
    err.status = 422
    applyDiscount.mockRejectedValueOnce(err)

    const { onApplied } = renderModal()
    fireEvent.change(percentageInput(), { target: { value: '10' } })
    fireEvent.click(submitBtn())

    expect(await screen.findByRole('alert')).toHaveTextContent('MercadoPago o QR')
    expect(onApplied).not.toHaveBeenCalled()
  })

  test('cada código produce un mensaje distinto', () => {
    const e400 = Object.assign(new Error('Validation failed'), { status: 400 })
    const e422 = Object.assign(new Error('Pago de gateway detectado'), { status: 422 })
    const e500 = Object.assign(new Error('boom'), { status: 500 })

    expect(discountErrorMessage(e400)).not.toBe(discountErrorMessage(e422))
    expect(discountErrorMessage(e422)).toBe('Pago de gateway detectado')
    expect(discountErrorMessage(e500)).toBe('No se pudo aplicar el descuento. Intentá de nuevo.')
    expect(discountErrorMessage(new Error('network_error'))).toContain('Sin conexión')
  })
})

// ── Gating del botón (canApplyDiscount) ───────────────────────

describe('canApplyDiscount', () => {
  test('lo permite a MANAGER y ADMIN sobre un pedido en efectivo', () => {
    expect(canApplyDiscount(ORDER, 'MANAGER')).toBe(true)
    expect(canApplyDiscount(ORDER, 'ADMIN')).toBe(true)
  })

  test('lo niega a STAFF', () => {
    expect(canApplyDiscount(ORDER, 'STAFF')).toBe(false)
  })

  test('lo niega con un pago de gateway aprobado o pendiente', () => {
    const mpApproved = { ...ORDER, paymentMethod: 'MERCADOPAGO', paymentStatus: 'APPROVED' }
    const mpPending = { ...ORDER, paymentMethod: 'MERCADOPAGO', paymentStatus: 'PENDING' }
    const qrPending = { ...ORDER, paymentMethod: 'QR_CODE', paymentStatus: 'PENDING' }

    expect(canApplyDiscount(mpApproved, 'MANAGER')).toBe(false)
    expect(canApplyDiscount(mpPending, 'MANAGER')).toBe(false)
    expect(canApplyDiscount(qrPending, 'ADMIN')).toBe(false)
  })

  test('lo permite con un pago de gateway rechazado: no hay cobro que descalzar', () => {
    const mpRejected = { ...ORDER, paymentMethod: 'MERCADOPAGO', paymentStatus: 'REJECTED' }
    expect(canApplyDiscount(mpRejected, 'MANAGER')).toBe(true)
  })

  test('lo niega en PENDING_PAYMENT: todavía no está definido cómo se cobra', () => {
    expect(canApplyDiscount({ ...ORDER, status: 'PENDING_PAYMENT' }, 'ADMIN')).toBe(false)
  })

  test('lo permite en toda la ventana activa, no solo en RECEIVED', () => {
    for (const status of ['RECEIVED', 'IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP']) {
      expect(canApplyDiscount({ ...ORDER, status }, 'MANAGER')).toBe(true)
    }
  })

  test('cierra la ventana al entregar: el total ya se factura en el resumen', () => {
    expect(canApplyDiscount({ ...ORDER, status: 'DELIVERED' }, 'ADMIN')).toBe(false)
  })

  test('lo niega en CANCELLED: no hay nada que cobrar', () => {
    expect(canApplyDiscount({ ...ORDER, status: 'CANCELLED' }, 'ADMIN')).toBe(false)
  })

  test('lo niega con una cancelación pendiente de resolver', () => {
    expect(canApplyDiscount({ ...ORDER, status: 'CANCELLATION_REQUESTED' }, 'ADMIN')).toBe(false)
  })
})
