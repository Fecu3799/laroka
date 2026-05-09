import { useEffect } from 'react'
import { ConfirmationScreen } from './ConfirmationScreen'
import styles from './PaymentResultScreen.module.css'

const KNOWN_STATUSES = ['approved', 'failure', 'pending']

function parseResult() {
  const params = new URLSearchParams(window.location.search)
  return {
    status: params.get('status'),
    orderId: params.get('orderId'),
  }
}

function XIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M8 8l8 8M16 8l-8 8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
      <polyline points="12 6 12 12 16 14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function PaymentResultScreen({ branchId, onComplete }) {
  const { status, orderId } = parseResult()

  const isInvalid =
    !status ||
    !KNOWN_STATUSES.includes(status) ||
    (status === 'approved' && !orderId)

  useEffect(() => {
    if (isInvalid) {
      onComplete()
      return
    }
    if (status === 'approved') {
      window.dispatchEvent(new Event('laroka_orders_updated'))
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (isInvalid) return null

  if (status === 'approved') {
    return (
      <ConfirmationScreen
        orderId={orderId}
        branchId={branchId}
        onComplete={onComplete}
      />
    )
  }

  if (status === 'failure') {
    return (
      <div className={styles.screen}>
        <div className={styles.content}>
          <div className={`${styles.iconWrapper} ${styles.iconDanger}`}>
            <XIcon />
          </div>
          <h1 className={styles.title}>Pago rechazado</h1>
          <p className={styles.message}>
            Tu pago fue rechazado. Podés reintentar o elegir otro medio de pago.
          </p>
          <button className={styles.btn} onClick={onComplete}>
            VOLVER AL MENÚ
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.screen}>
      <div className={styles.content}>
        <div className={`${styles.iconWrapper} ${styles.iconAccent}`}>
          <ClockIcon />
        </div>
        <h1 className={styles.title}>Pago en proceso</h1>
        <p className={styles.message}>
          Tu pago está siendo procesado. Tu pedido aparecerá en seguimiento cuando sea confirmado.
        </p>
        <button className={styles.btn} onClick={onComplete}>
          IR AL MENÚ
        </button>
      </div>
    </div>
  )
}
