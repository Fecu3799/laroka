import { useState, useEffect, useRef, useCallback } from 'react'
import { usePreferredBranch } from '../hooks/usePreferredBranch'
import { readActiveOrders, removeActiveOrder } from '../utils/activeOrders'
import { cancelOrder } from '../services/ordersService'
import styles from './OrderTrackingBanner.module.css'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const POLL_INTERVAL = 15_000
const TERMINAL_STATUSES = ['DELIVERED', 'CANCELLED']

const PROGRESS = {
  PENDING_PAYMENT: 0,
  RECEIVED: 10,
  IN_PREPARATION: 30,
  ON_THE_WAY: 70,
  READY_FOR_PICKUP: 70,
  DELIVERED: 100,
}

const STATUS_LABELS = {
  PENDING_PAYMENT: 'PAGO EN PROCESO',
  RECEIVED: 'RECIBIDO',
  IN_PREPARATION: 'EN PREPARACIÓN',
  ON_THE_WAY: 'EN CAMINO',
  READY_FOR_PICKUP: 'LISTO P/RETIRAR',
  DELIVERED: 'ENTREGADO',
  CANCELLED: 'CANCELADO',
  CANCELLATION_REQUESTED: 'CANCELACIÓN SOL.',
}


const DELIVERY_STEPS = ['RECEIVED', 'IN_PREPARATION', 'ON_THE_WAY', 'DELIVERED']
const TAKEAWAY_STEPS = ['RECEIVED', 'IN_PREPARATION', 'READY_FOR_PICKUP', 'DELIVERED']

const STEP_LABELS = {
  RECEIVED: 'Recibido',
  IN_PREPARATION: 'En preparación',
  ON_THE_WAY: 'En camino',
  READY_FOR_PICKUP: 'Listo para retirar',
  DELIVERED: 'Entregado',
}

function formatPrice(amount) {
  return `$${Number(amount).toLocaleString('es-AR')}`
}

function formatTime(isoString) {
  return new Date(isoString).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
}

function ItemsSkeleton() {
  return (
    <div className={styles.itemsSkeleton}>
      {[140, 180, 110].map((w, i) => (
        <div key={i} className={styles.skeletonItemRow}>
          <div className={`${styles.skeletonBlock} ${styles.skeletonItemName}`} style={{ width: w }} />
          <div className={`${styles.skeletonBlock} ${styles.skeletonItemPrice}`} />
        </div>
      ))}
    </div>
  )
}

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
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

function ConfirmCancelModal({ isRequest, onConfirm, onClose, loading, error }) {
  return (
    <div
      className={styles.modalOverlay}
      role="dialog"
      aria-modal="true"
      onClick={(e) => { if (e.target === e.currentTarget && !loading) onClose() }}
    >
      <div className={styles.modalBox}>
        <p className={styles.modalTitle}>
          {isRequest ? '¿Solicitar cancelación?' : '¿Cancelar pedido?'}
        </p>
        <p className={styles.modalBody}>
          {isRequest
            ? 'Se enviará una solicitud al local. La decisión final queda a cargo del local.'
            : 'El pedido será cancelado y no podrá reactivarse.'}
        </p>
        {error && <p className={styles.modalError}>{error}</p>}
        <div className={styles.modalActions}>
          <button
            className={styles.modalBtnDismiss}
            onClick={onClose}
            disabled={loading}
          >
            VOLVER
          </button>
          <button
            className={styles.modalBtnConfirm}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'PROCESANDO...' : isRequest ? 'SOLICITAR' : 'CANCELAR'}
          </button>
        </div>
      </div>
    </div>
  )
}

function OrderSlide({ orderId, order, isExpanded, items, itemsLoading, itemsError, onToggleExpand, estimatedDeliveryMinutes, onPhoneClick, onOrderUpdate }) {
  const [showModal, setShowModal] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState(null)

  const canDirectCancel = order?.status === 'RECEIVED'
  const canRequestCancel = order?.status === 'IN_PREPARATION'

  const handleOpenModal = () => {
    setCancelError(null)
    setShowModal(true)
  }

  const handleConfirmCancel = async () => {
    const isDirectCancel = order?.status === 'RECEIVED'
    setCancelling(true)
    setCancelError(null)
    try {
      await cancelOrder(orderId)
      setShowModal(false)
      onOrderUpdate(orderId, isDirectCancel ? 'CANCELLED' : 'CANCELLATION_REQUESTED')
    } catch (err) {
      setCancelError(err.is422 ? err.message : 'No se pudo procesar la solicitud.')
    } finally {
      setCancelling(false)
    }
  }

  if (!order) {
    return (
      <div className={styles.slideContent} aria-busy="true" aria-label="Cargando pedido">
        <div className={`${styles.skeletonBlock} ${styles.skeletonTitle}`} />
        <div className={`${styles.skeletonBlock} ${styles.skeletonBadge}`} />
        <div className={`${styles.skeletonBlock} ${styles.skeletonProgress}`} />
      </div>
    )
  }

  const isRequest = canRequestCancel

  const progress = PROGRESS[order.status] ?? 10
  const isPendingPayment = order.status === 'PENDING_PAYMENT'
  const isDelivery = order.orderType === 'DELIVERY'
  const steps = isDelivery ? DELIVERY_STEPS : TAKEAWAY_STEPS
  const historyMap = {}
  for (const h of (order.history ?? [])) historyMap[h.toStatus] = h

  return (
    <>
    {showModal && (
      <ConfirmCancelModal
        isRequest={isRequest}
        onConfirm={handleConfirmCancel}
        onClose={() => { if (!cancelling) setShowModal(false) }}
        loading={cancelling}
        error={cancelError}
      />
    )}
    <div className={styles.slideContent}>
      <div className={styles.topRow}>
        <div className={styles.titleBlock}>
          <span className={styles.title}>
            {order.status === 'PENDING_PAYMENT' ? 'Pago en proceso' : 'Pedido en proceso'}
          </span>
          {!isPendingPayment && estimatedDeliveryMinutes && (
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

      {!isPendingPayment && (
        <div className={styles.progressRow} data-testid="progress-bar">
          <span className={styles.progressEmoji} aria-hidden="true">🏪</span>
          <div className={styles.progressTrack}>
            <div className={styles.progressFill} style={{ width: `${progress}%` }} />
            <span className={styles.scooter} style={{ left: `${progress}%` }} aria-hidden="true">
              🛵
            </span>
          </div>
          <span className={styles.progressEmoji} aria-hidden="true">🏠</span>
        </div>
      )}

      {isExpanded && (
        <div className={styles.expandPanel}>
          <div className={styles.panelSeparator} />

          <p className={styles.sectionLabel}>HISTORIAL</p>
          <ul className={styles.historyList}>
            {steps.map(step => {
              const entry = historyMap[step]
              return (
                <li key={step} className={styles.historyItem}>
                  <span className={styles.historyBullet} data-done={!!entry} />
                  <span className={styles.historyLabel} data-done={!!entry}>
                    {STEP_LABELS[step]}
                  </span>
                  <span className={styles.historyTime}>
                    {entry ? formatTime(entry.changedAt) : '—'}
                  </span>
                </li>
              )
            })}
          </ul>

          <p className={styles.sectionLabel}>TU PEDIDO</p>
          {itemsLoading && <ItemsSkeleton />}
          {itemsError && (
            <p className={styles.itemsError}>No se pudieron cargar los items.</p>
          )}
          {items && (
            <>
              <ul className={styles.itemsList}>
                {items.map((item, i) => (
                  <li key={i} className={styles.itemRow}>
                    <span className={styles.itemQtyName}>{item.quantity}× {item.name}</span>
                    <span className={styles.itemPrice}>{formatPrice(item.subtotal)}</span>
                  </li>
                ))}
              </ul>
              <div className={styles.totalsContainer}>
                <div className={styles.totalRow}>
                  <span>Subtotal</span>
                  <span>{formatPrice(order.subtotal)}</span>
                </div>
                {isDelivery && (
                  <div className={styles.totalRow}>
                    <span>Cargo de delivery</span>
                    <span>{formatPrice(order.deliveryFee)}</span>
                  </div>
                )}
                <div className={styles.totalRow}>
                  <span>Cargo de servicio</span>
                  <span>{formatPrice(order.serviceFee)}</span>
                </div>
                <div className={styles.totalsSeparator} />
                <div className={`${styles.totalRow} ${styles.totalRowFinal}`}>
                  <span>Total</span>
                  <span>{formatPrice(order.totalAmount)}</span>
                </div>
              </div>
            </>
          )}

          {order.status === 'CANCELLATION_REQUESTED' && (
            <p className={styles.cancelMessage}>
              Cancelación solicitada, esperando respuesta del local.
            </p>
          )}
          {canDirectCancel && (
            <button className={styles.cancelBtn} onClick={handleOpenModal}>
              CANCELAR PEDIDO
            </button>
          )}
          {canRequestCancel && (
            <button className={styles.cancelBtn} onClick={handleOpenModal}>
              SOLICITAR CANCELACIÓN
            </button>
          )}
        </div>
      )}

      {!isPendingPayment && (
        <button className={styles.expandBtn} onClick={onToggleExpand}>
          {isExpanded ? 'OCULTAR DETALLES' : 'VER DETALLES'}
          <span className={`${styles.expandChevron}${isExpanded ? ` ${styles.expandChevronOpen}` : ''}`}>
            <ChevronIcon />
          </span>
        </button>
      )}
    </div>
    </>
  )
}

export function OrderTrackingBanner({ branchId }) {
  const { estimatedDeliveryMinutes, phone } = usePreferredBranch()

  // All tracked entries — polling runs on all of them regardless of branch
  const [orderEntries, setOrderEntries] = useState(() => readActiveOrders())
  const [ordersData, setOrdersData] = useState({})
  const [activeIndex, setActiveIndex] = useState(0)
  const [isExpanded, setIsExpanded] = useState(false)
  const [ordersItems, setOrdersItems] = useState({})

  const orderEntriesRef = useRef(orderEntries)
  const ordersItemsRef = useRef(ordersItems)
  const touchStartRef = useRef(null)
  const visibleEntriesRef = useRef([])
  const clampedIndexRef = useRef(0)
  const isExpandedRef = useRef(false)

  // Derived from prop — recalculates immediately whenever branchId changes
  const visibleEntries = orderEntries.filter(e => e.branchId === branchId)
  const n = visibleEntries.length
  const clampedIndex = Math.min(activeIndex, Math.max(0, n - 1))

  // Keep refs in sync after every render
  useEffect(() => {
    orderEntriesRef.current = orderEntries
    ordersItemsRef.current = ordersItems
    isExpandedRef.current = isExpanded
    visibleEntriesRef.current = visibleEntries
    clampedIndexRef.current = clampedIndex
  })

  // Merge newly added entries without restarting the poll
  const reloadOrders = useCallback(() => {
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

  const fetchItems = useCallback(async (orderId) => {
    const cur = ordersItemsRef.current[orderId]
    if (cur?.loading || cur?.data != null) return
    ordersItemsRef.current = {
      ...ordersItemsRef.current,
      [orderId]: { data: null, loading: true, error: false },
    }
    setOrdersItems(prev => ({ ...prev, [orderId]: { data: null, loading: true, error: false } }))
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/items`)
      if (!res.ok) throw new Error()
      const data = await res.json()
      setOrdersItems(prev => ({ ...prev, [orderId]: { data, loading: false, error: false } }))
    } catch {
      setOrdersItems(prev => ({ ...prev, [orderId]: { data: null, loading: false, error: true } }))
    }
  }, [])

  const handleToggleExpand = useCallback(() => {
    const opening = !isExpandedRef.current
    setIsExpanded(opening)
    if (opening) {
      const entries = visibleEntriesRef.current
      const activeId = entries[clampedIndexRef.current]?.orderId
      if (activeId) fetchItems(activeId)
      let delay = 300
      for (const { orderId } of entries) {
        if (orderId !== activeId) {
          setTimeout(() => fetchItems(orderId), delay)
          delay += 300
        }
      }
    }
  }, [fetchItems])

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
            removeActiveOrder(orderId)
            setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
            setOrdersData(prev => {
              const next = { ...prev }
              delete next[orderId]
              return next
            })
          } else {
            setOrdersData(prev => ({ ...prev, [orderId]: data }))
          }
        } catch {
          // ignore
        }
      }
    }

    poll()
    const interval = setInterval(poll, POLL_INTERVAL)
    return () => {
      active = false
      clearInterval(interval)
    }
  // Boolean dep: interval only created/destroyed when transitioning 0 ↔ 1+
  // eslint-disable-next-line react-hooks/exhaustive-deps
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

  const handleOrderUpdate = useCallback((orderId, newStatus) => {
    if (TERMINAL_STATUSES.includes(newStatus)) {
      removeActiveOrder(orderId)
      setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
      setOrdersData(prev => {
        const next = { ...prev }
        delete next[orderId]
        return next
      })
    } else {
      setOrdersData(prev => ({
        ...prev,
        [orderId]: { ...prev[orderId], status: newStatus },
      }))
    }
  }, [])

  const handlePhoneClick = () => {
    if (phone && window.confirm('¿Llamar al local?')) {
      window.location.href = `tel:${phone}`
    }
  }

  // Polling runs in background even when nothing is visible for this branch
  if (n === 0) return null

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
                  orderId={orderId}
                  order={ordersData[orderId]}
                  isExpanded={isExpanded}
                  items={ordersItems[orderId]?.data ?? null}
                  itemsLoading={ordersItems[orderId]?.loading ?? false}
                  itemsError={ordersItems[orderId]?.error ?? false}
                  onToggleExpand={handleToggleExpand}
                  estimatedDeliveryMinutes={estimatedDeliveryMinutes}
                  onPhoneClick={handlePhoneClick}
                  onOrderUpdate={handleOrderUpdate}
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
