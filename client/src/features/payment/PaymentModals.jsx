import { useState, useEffect } from 'react'
import { initiatePayment } from '../../services/paymentsService'
import { cancelOrder } from '../../services/ordersService'
import { removeActiveOrder } from '../../utils/activeOrders'
import styles from './PaymentModals.module.css'

function XCircleIcon() {
  return (
    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 8l8 8M16 8l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
      <polyline points="12 6 12 12 16 14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function FailureModal({ orderId, formData, cartItems, onClose }) {
  const [retrying, setRetrying] = useState(false)
  const [retryError, setRetryError] = useState(null)

  const handleRetry = async () => {
    // Re-persist recovery so a second failure also restores state
    if (orderId && formData) {
      sessionStorage.setItem('laroka_checkout_recovery', JSON.stringify({
        orderId,
        items: (cartItems || []).map(i => ({
          id: i.id, name: i.name, price: i.price, qty: i.qty,
          imageUrl: i.imageUrl || null, description: i.description || null,
        })),
        formData,
      }))
    }
    setRetryError(null)
    setRetrying(true)
    try {
      const paymentLink = await initiatePayment(orderId)
      window.location.href = paymentLink
    } catch {
      sessionStorage.removeItem('laroka_checkout_recovery')
      setRetryError('No se pudo iniciar el pago. Intentá nuevamente.')
      setRetrying(false)
    }
  }

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-labelledby="failure-modal-title">
      <div className={styles.card}>
        <div className={`${styles.iconWrapper} ${styles.iconDanger}`}>
          <XCircleIcon />
        </div>
        <h2 id="failure-modal-title" className={styles.title}>Pago rechazado</h2>
        <p className={styles.message}>
          Tu pago no pudo procesarse. Podés reintentar o elegir otro medio de pago.
        </p>
        {retryError && <p className={styles.retryError}>{retryError}</p>}
        <button className={styles.btnPrimary} onClick={handleRetry} disabled={retrying}>
          {retrying ? 'PROCESANDO...' : 'REINTENTAR'}
        </button>
        <button className={styles.btnSecondary} onClick={onClose} disabled={retrying}>
          Probar otro método de pago
        </button>
      </div>
    </div>
  )
}

export function PendingPaymentModal({ orderId, onCancel }) {
  const [loading, setLoading] = useState(null) // 'continue' | 'cancel'
  const [error, setError] = useState(null)

  useEffect(() => {
    if (import.meta.env.VITE_DEV_MP_DEBUG_LOGS === 'true') console.log('[MP-DEBUG] PendingPaymentModal mounted — orderId:', orderId)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleContinue = async () => {
    setError(null)
    setLoading('continue')
    try {
      const paymentLink = await initiatePayment(orderId)
      window.location.href = paymentLink
    } catch {
      setError('No se pudo obtener el link de pago. Intentá nuevamente.')
      setLoading(null)
    }
  }

  const handleCancel = async () => {
    setError(null)
    setLoading('cancel')
    try {
      await cancelOrder(orderId, null)
      removeActiveOrder(orderId)
      sessionStorage.removeItem('laroka_checkout_recovery')
      onCancel()
    } catch {
      setError('No se pudo cancelar el pedido. Intentá nuevamente.')
      setLoading(null)
    }
  }

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-labelledby="pending-payment-title">
      <div className={styles.card}>
        <div className={`${styles.iconWrapper} ${styles.iconAccent}`}>
          <ClockIcon />
        </div>
        <h2 id="pending-payment-title" className={styles.title}>Tenés un pago pendiente</h2>
        <p className={styles.message}>
          Hay un pedido esperando ser pagado. Podés continuar con el pago o cancelarlo.
        </p>
        {error && <p className={styles.retryError}>{error}</p>}
        <button
          className={styles.btnPrimary}
          onClick={handleContinue}
          disabled={loading !== null}
        >
          {loading === 'continue' ? 'PROCESANDO...' : 'CONTINUAR PAGO'}
        </button>
        <button
          className={styles.btnSecondary}
          onClick={handleCancel}
          disabled={loading !== null}
        >
          {loading === 'cancel' ? 'Cancelando...' : 'Cancelar pedido'}
        </button>
      </div>
    </div>
  )
}

export function PendingModal({ onClose }) {
  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-labelledby="pending-modal-title">
      <div className={styles.card}>
        <div className={`${styles.iconWrapper} ${styles.iconAccent}`}>
          <ClockIcon />
        </div>
        <h2 id="pending-modal-title" className={styles.title}>Pago en proceso</h2>
        <p className={styles.message}>
          Tu pago está siendo procesado. Tu pedido aparecerá en seguimiento cuando sea confirmado.
        </p>
        <button className={styles.btnPrimary} onClick={onClose}>
          ENTENDIDO
        </button>
      </div>
    </div>
  )
}
