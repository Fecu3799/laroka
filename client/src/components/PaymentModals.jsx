import { useState } from 'react'
import { initiatePayment } from '../services/paymentsService'
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
