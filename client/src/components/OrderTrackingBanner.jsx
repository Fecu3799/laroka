import { useState, useEffect, useRef, useCallback } from 'react'
import { usePreferredBranch } from '../hooks/usePreferredBranch'
import styles from './OrderTrackingBanner.module.css'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const POLL_INTERVAL = 15_000
const TERMINAL_STATUSES = ['DELIVERED', 'CANCELLED']

const PROGRESS = {
  RECEIVED: 10,
  IN_PREPARATION: 30,
  ON_THE_WAY: 70,
  READY_FOR_PICKUP: 70,
  DELIVERED: 100,
}

const STATUS_LABELS = {
  RECEIVED: 'RECIBIDO',
  IN_PREPARATION: 'EN PREPARACIÓN',
  ON_THE_WAY: 'EN CAMINO',
  READY_FOR_PICKUP: 'LISTO P/RETIRAR',
  DELIVERED: 'ENTREGADO',
  CANCELLED: 'CANCELADO',
  CANCELLATION_REQUESTED: 'CANCELACIÓN SOL.',
}

function readActiveOrders() {
  try {
    const raw = localStorage.getItem('laroka_active_orders')
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function removeFromStorage(orderId) {
  try {
    const current = readActiveOrders()
    localStorage.setItem('laroka_active_orders', JSON.stringify(current.filter(id => id !== orderId)))
  } catch {}
}

function PhoneIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 11.5 19.79 19.79 0 01.01 2.88 2 2 0 012 .7h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L6.09 8.69a16 16 0 006.22 6.22l1.06-1.06a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function OrderTrackingBanner() {
  const { estimatedDeliveryMinutes, phone } = usePreferredBranch()

  const [orderIds, setOrderIds] = useState(() => readActiveOrders())
  const [ordersData, setOrdersData] = useState({})

  // Always-current ref to avoid stale closures inside the interval
  const orderIdsRef = useRef(orderIds)
  orderIdsRef.current = orderIds

  // Re-read localStorage and append any new orderIds without restarting the poll
  const reloadOrders = useCallback(() => {
    const fresh = readActiveOrders()
    setOrderIds(prev => {
      const existing = new Set(prev)
      const added = fresh.filter(id => !existing.has(id))
      return added.length > 0 ? [...prev, ...added] : prev
    })
  }, [])

  useEffect(() => {
    window.addEventListener('laroka_orders_updated', reloadOrders)
    window.addEventListener('storage', reloadOrders)
    return () => {
      window.removeEventListener('laroka_orders_updated', reloadOrders)
      window.removeEventListener('storage', reloadOrders)
    }
  }, [reloadOrders])

  useEffect(() => {
    if (orderIds.length === 0) return

    let active = true

    const poll = async () => {
      for (const id of orderIdsRef.current) {
        if (!active) return
        try {
          const res = await fetch(`${API_BASE}/orders/${id}/status`)
          if (!res.ok) continue
          const data = await res.json()

          if (!active) return

          if (TERMINAL_STATUSES.includes(data.status)) {
            removeFromStorage(id)
            setOrderIds(prev => prev.filter(oid => oid !== id))
            setOrdersData(prev => {
              const next = { ...prev }
              delete next[id]
              return next
            })
          } else {
            setOrdersData(prev => ({ ...prev, [id]: data }))
          }
        } catch {}
      }
    }

    poll()
    const interval = setInterval(poll, POLL_INTERVAL)
    return () => {
      active = false
      clearInterval(interval)
    }
  // Dep booleano: el intervalo solo se crea/destruye cuando se pasa de 0↔1+
  // Agregar orderIds nuevos solo actualiza el ref, no reinicia el intervalo
  }, [orderIds.length > 0])

  if (orderIds.length === 0) return null

  // Show first order that has data; skip while loading
  const activeId = orderIds.find(id => ordersData[id])
  if (!activeId) return null

  const order = ordersData[activeId]
  const progress = PROGRESS[order.status] ?? 10
  const isDelivery = order.orderType === 'DELIVERY'

  const handlePhoneClick = () => {
    if (phone && window.confirm('¿Llamar al local?')) {
      window.location.href = `tel:${phone}`
    }
  }

  return (
    <div className={styles.banner}>
      {/* Fila superior */}
      <div className={styles.topRow}>
        <div className={styles.titleBlock}>
          <span className={styles.title}>Pedido en proceso</span>
          {estimatedDeliveryMinutes && (
            <span className={styles.eta}>Llega en ~{estimatedDeliveryMinutes} min</span>
          )}
        </div>
        <button className={styles.phoneBtn} aria-label="Llamar al local" onClick={handlePhoneClick}>
          <PhoneIcon />
        </button>
      </div>

      {/* Badge de estado + dirección */}
      <div className={styles.metaRow}>
        <span className={styles.badge} data-status={order.status}>
          {STATUS_LABELS[order.status] ?? order.status}
        </span>
        {isDelivery && order.deliveryAddress && (
          <span className={styles.address}>{order.deliveryAddress}</span>
        )}
      </div>

      {/* Barra de progreso */}
      <div className={styles.progressRow}>
        <span className={styles.progressEmoji} aria-hidden="true">🏪</span>
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} style={{ width: `${progress}%` }} />
          <span
            className={styles.scooter}
            style={{ left: `${progress}%` }}
            aria-hidden="true"
          >
            🛵
          </span>
        </div>
        <span className={styles.progressEmoji} aria-hidden="true">🏠</span>
      </div>
    </div>
  )
}
