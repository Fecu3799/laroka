import { useState, useEffect } from 'react'
import { ConfirmationScreen } from './ConfirmationScreen'

const KNOWN_STATUSES = ['approved', 'failure', 'pending']

function parseResult() {
  const params = new URLSearchParams(window.location.search)
  return {
    status: params.get('status'),
    orderId: params.get('orderId'),
  }
}

export function PaymentResultScreen({ branchId, onComplete }) {
  const { status, orderId } = parseResult()

  const [recovery] = useState(() => {
    const raw = sessionStorage.getItem('pedisur_checkout_recovery')
    try { return raw ? JSON.parse(raw) : null } catch { return null }
  })

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
      sessionStorage.removeItem('pedisur_checkout_recovery')
      window.dispatchEvent(new Event('pedisur_orders_updated'))
      return
    }
    if (status === 'pending') {
      sessionStorage.removeItem('pedisur_checkout_recovery')
      onComplete({ type: 'pending' })
      return
    }
    if (status === 'failure') {
      sessionStorage.removeItem('pedisur_checkout_recovery')
      onComplete({ type: 'failure', recovery })
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (isInvalid || status === 'failure' || status === 'pending') return null

  if (status === 'approved') {
    return (
      <ConfirmationScreen
        orderId={orderId}
        branchId={branchId}
        onComplete={onComplete}
      />
    )
  }

  return null
}
