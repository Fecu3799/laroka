import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { PaymentResultScreen } from '../pages/PaymentResultScreen'

vi.mock('../pages/ConfirmationScreen', () => ({
  ConfirmationScreen: ({ orderId }) => <div>Confirmación {orderId}</div>,
}))

function setSearch(search) {
  window.history.pushState({}, '', search || '/')
}

describe('PaymentResultScreen', () => {
  let onComplete

  beforeEach(() => {
    onComplete = vi.fn()
    setSearch('/')
    sessionStorage.clear()
  })

  afterEach(() => {
    setSearch('/')
  })

  it('status=approved: renderiza ConfirmationScreen y no llama onComplete de inmediato', async () => {
    sessionStorage.setItem('laroka_checkout_recovery', JSON.stringify({ orderId: 'x' }))
    setSearch('?status=approved&orderId=order-99')

    render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    expect(screen.getByText('Confirmación order-99')).toBeInTheDocument()
    await waitFor(() => {
      expect(sessionStorage.getItem('laroka_checkout_recovery')).toBeNull()
    })
    expect(onComplete).not.toHaveBeenCalled()
  })

  it('status=failure: renderiza null y llama onComplete con type=failure y recovery', async () => {
    const recovery = {
      orderId: 'order-42',
      items: [{ id: 1, name: 'Muzarella', price: 2000, qty: 1 }],
      formData: { orderType: 'takeaway', nombre: 'Juan', telefono: '11' },
    }
    sessionStorage.setItem('laroka_checkout_recovery', JSON.stringify(recovery))
    setSearch('?status=failure&orderId=order-42')

    const { container } = render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    expect(container).toBeEmptyDOMElement()
    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'failure',
          recovery: expect.objectContaining({ orderId: 'order-42' }),
        })
      )
    })
    expect(sessionStorage.getItem('laroka_checkout_recovery')).toBeNull()
  })

  it('status=failure: llama onComplete con recovery=null si no hay dato en sessionStorage', async () => {
    setSearch('?status=failure&orderId=order-42')

    render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'failure', recovery: null })
      )
    })
  })

  it('status=pending: renderiza null y llama onComplete con type=pending', async () => {
    setSearch('?status=pending')

    const { container } = render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    expect(container).toBeEmptyDOMElement()
    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith({ type: 'pending' })
    })
  })

  it('status inválido: llama onComplete sin argumentos', async () => {
    setSearch('?status=unknown')

    render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith()
    })
  })

  it('sin parámetros: llama onComplete sin argumentos', async () => {
    render(<PaymentResultScreen branchId={1} onComplete={onComplete} />)

    await waitFor(() => {
      expect(onComplete).toHaveBeenCalledWith()
    })
  })
})
