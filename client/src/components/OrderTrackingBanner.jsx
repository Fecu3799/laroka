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
    localStorage.setItem(
      'laroka_active_orders',
      JSON.stringify(current.filter(id => id !== orderId))
    )
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

function OrderSlide({ order, estimatedDeliveryMinutes, onPhoneClick }) {
  if (!order) {
    return <div className={styles.slideLoading} aria-busy="true" />
  }

  const progress = PROGRESS[order.status] ?? 10
  const isDelivery = order.orderType === 'DELIVERY'

  return (
    <div className={styles.slideContent}>
      <div className={styles.topRow}>
        <div className={styles.titleBlock}>
          <span className={styles.title}>Pedido en proceso</span>
          {estimatedDeliveryMinutes && (
            <span className={styles.eta}>Llega en ~{estimatedDeliveryMinutes} min</span>
          )}
        </div>
        <button className={styles.phoneBtn} aria-label="Llamar al local" onClick={onPhoneClick}>
          <PhoneIcon />
        </button>
      </div>

      <div className={styles.metaRow}>
        <span className={styles.badge} data-status={order.status}>
          {STATUS_LABELS[order.status] ?? order.status}
        </span>
        {isDelivery && order.deliveryAddress && (
          <span className={styles.address}>{order.deliveryAddress}</span>
        )}
      </div>

      <div className={styles.progressRow}>
        <span className={styles.progressEmoji} aria-hidden="true">🏪</span>
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} style={{ width: `${progress}%` }} />
          <span className={styles.scooter} style={{ left: `${progress}%` }} aria-hidden="true">
            🛵
          </span>
        </div>
        <span className={styles.progressEmoji} aria-hidden="true">🏠</span>
      </div>
    </div>
  )
}

export function OrderTrackingBanner() {
  const { estimatedDeliveryMinutes, phone } = usePreferredBranch()

  const [orderIds, setOrderIds] = useState(() => readActiveOrders())
  const [ordersData, setOrdersData] = useState({})
  const [activeIndex, setActiveIndex] = useState(0)

  const orderIdsRef = useRef(orderIds)
  orderIdsRef.current = orderIds

  const touchStartRef = useRef(null)

  // Clamp activeIndex when an order is removed
  useEffect(() => {
    setActiveIndex(prev => Math.min(prev, Math.max(0, orderIds.length - 1)))
  }, [orderIds.length])

  // Re-read localStorage and append new orderIds without restarting the poll
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

  // Single interval iterates all active orderIds via ref — no restart on add
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
  // Boolean dep: interval only created/destroyed when transitioning 0 ↔ 1+
  }, [orderIds.length > 0])

  const handleTouchStart = (e) => {
    touchStartRef.current = e.touches[0].clientX
  }

  const handleTouchEnd = (e) => {
    if (touchStartRef.current === null) return
    const delta = e.changedTouches[0].clientX - touchStartRef.current
    const n = orderIdsRef.current.length
    if (delta < -50) {
      setActiveIndex(i => Math.min(n - 1, i + 1))
    } else if (delta > 50) {
      setActiveIndex(i => Math.max(0, i - 1))
    }
    touchStartRef.current = null
  }

  const handlePhoneClick = () => {
    if (phone && window.confirm('¿Llamar al local?')) {
      window.location.href = `tel:${phone}`
    }
  }

  if (orderIds.length === 0) return null

  const n = orderIds.length
  const clampedIndex = Math.min(activeIndex, n - 1)

  return (
    <>
      <div className={styles.banner}>
        <div
          className={styles.slidesOuter}
          onTouchStart={n > 1 ? handleTouchStart : undefined}
          onTouchEnd={n > 1 ? handleTouchEnd : undefined}
        >
          <div
            className={styles.slidesInner}
            style={{
              width: `${100 * n}%`,
              transform: `translateX(${-clampedIndex * (100 / n)}%)`,
            }}
          >
            {orderIds.map(id => (
              <div
                key={id}
                className={styles.slide}
                style={{ width: `${100 / n}%` }}
              >
                <OrderSlide
                  order={ordersData[id]}
                  estimatedDeliveryMinutes={estimatedDeliveryMinutes}
                  onPhoneClick={handlePhoneClick}
                />
              </div>
            ))}
          </div>
        </div>
      </div>

      {n > 1 && (
        <div className={styles.dots} aria-hidden="true">
          {orderIds.map((id, i) => (
            <div
              key={id}
              className={styles.dot}
              data-active={i === clampedIndex}
            />
          ))}
        </div>
      )}
    </>
  )
}
