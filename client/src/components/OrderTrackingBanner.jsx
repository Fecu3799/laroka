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
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return parsed.map(e =>
      typeof e === 'object' && e && e.orderId
        ? e
        : { orderId: e, branchId: null }
    )
  } catch {
    return []
  }
}

function removeFromStorage(orderId) {
  try {
    const current = readActiveOrders()
    localStorage.setItem(
      'laroka_active_orders',
      JSON.stringify(current.filter(e => e.orderId !== orderId))
    )
    window.dispatchEvent(new Event('laroka_orders_updated'))
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
    return (
      <div className={styles.slideContent} aria-busy="true" aria-label="Cargando pedido">
        <div className={`${styles.skeletonBlock} ${styles.skeletonTitle}`} />
        <div className={`${styles.skeletonBlock} ${styles.skeletonBadge}`} />
        <div className={`${styles.skeletonBlock} ${styles.skeletonProgress}`} />
      </div>
    )
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

export function OrderTrackingBanner({ branchId }) {
  const { estimatedDeliveryMinutes, phone } = usePreferredBranch()

  // All tracked entries — polling runs on all of them regardless of branch
  const [orderEntries, setOrderEntries] = useState(() => {
    try {
      const raw = localStorage.getItem('laroka_active_orders')
      return raw ? JSON.parse(raw) : []
    } catch {
      return []
    }
  })
  const [ordersData, setOrdersData] = useState({})
  const [activeIndex, setActiveIndex] = useState(0)

  const orderEntriesRef = useRef(orderEntries)
  orderEntriesRef.current = orderEntries

  const touchStartRef = useRef(null)

  // Derived from prop — recalculates immediately whenever branchId changes
  const visibleEntries = orderEntries.filter(e => e.branchId === branchId)
  const n = visibleEntries.length

  // Clamp activeIndex when a visible order is removed
  useEffect(() => {
    setActiveIndex(prev => Math.min(prev, Math.max(0, n - 1)))
  }, [n])

  // Merge newly added entries without restarting the poll
  const reloadOrders = useCallback(() => {
    console.log('BANNER EVENT RECEIVED')
    const fresh = readActiveOrders()
    setOrderEntries(prev => {
      const existingIds = new Set(prev.map(e => e.orderId))
      const added = fresh.filter(e => !existingIds.has(e.orderId))
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

  // Single interval polls all tracked entries via ref — no restart when one is added
  useEffect(() => {
    if (orderEntries.length === 0) return

    let active = true

    const poll = async () => {
      for (const entry of orderEntriesRef.current) {
        if (!active) return
        const { orderId } = entry
        try {
          const res = await fetch(`${API_BASE}/orders/${orderId}/status`)
          if (!res.ok) continue
          const data = await res.json()
          if (!active) return

          if (TERMINAL_STATUSES.includes(data.status)) {
            removeFromStorage(orderId)
            setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
            setOrdersData(prev => {
              const next = { ...prev }
              delete next[orderId]
              return next
            })
          } else {
            setOrdersData(prev => ({ ...prev, [orderId]: data }))
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
  }, [orderEntries.length > 0])

  const handleTouchStart = (e) => {
    touchStartRef.current = e.touches[0].clientX
  }

  const handleTouchEnd = (e) => {
    if (touchStartRef.current === null) return
    const delta = e.changedTouches[0].clientX - touchStartRef.current
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

  // Polling runs in background even when nothing is visible for this branch
  if (n === 0) return null

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
            {visibleEntries.map(({ orderId }) => (
              <div
                key={orderId}
                className={styles.slide}
                style={{ width: `${100 / n}%` }}
              >
                <OrderSlide
                  order={ordersData[orderId]}
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
          {visibleEntries.map(({ orderId }, i) => (
            <div
              key={orderId}
              className={styles.dot}
              data-active={i === clampedIndex}
            />
          ))}
        </div>
      )}
    </>
  )
}
