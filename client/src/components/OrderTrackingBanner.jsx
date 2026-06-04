import { useState, useEffect, useRef, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { usePreferredBranch } from '../hooks/usePreferredBranch'
import { readActiveOrders, removeActiveOrder } from '../utils/activeOrders'
import { cancelOrder } from '../services/ordersService'
import styles from './OrderTrackingBanner.module.css'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const POLL_INTERVAL = 15_000

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

// ── Cancellation reason modal ─────────────────────────────────

function CancellationReasonModal({ reason, onClose }) {
  return createPortal(
    <>
      <style>{`@keyframes _lrModalIn{from{opacity:0;transform:scale(.85)}to{opacity:1;transform:scale(1)}}`}</style>
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.75)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 300, padding: '16px', boxSizing: 'border-box',
        }}
        onClick={(e) => { if (e.target === e.currentTarget) onClose() }}
      >
        <div className={styles.reasonModalBox}>
          <p className={styles.sectionLabel}>MOTIVO DE CANCELACIÓN</p>
          <p
            className={styles.reasonText}
            style={{ opacity: reason ? 1 : 0.45, fontStyle: reason ? 'normal' : 'italic' }}
          >
            {reason ?? 'Sin motivo indicado.'}
          </p>
          <button className={styles.modalBtnDismiss} onClick={onClose}>Cerrar</button>
        </div>
      </div>
    </>,
    document.body
  )
}

// ── Detail modal ──────────────────────────────────────────────

function OrderDetailModal({
  order,
  items,
  itemsLoading,
  itemsError,
  confirmingCancel,
  cancelling,
  cancelError,
  canDirectCancel,
  canRequestCancel,
  cancelReason,
  onReasonChange,
  onClose,
  onRequestCancel,
  onConfirmCancel,
  onCancelBack,
}) {
  const isDelivery = order.orderType === 'DELIVERY'
  const steps = isDelivery ? DELIVERY_STEPS : TAKEAWAY_STEPS
  const historyMap = {}
  for (const h of (order.history ?? [])) historyMap[h.toStatus] = h

  return createPortal(
    <>
      <style>{`@keyframes _lrModalIn{from{opacity:0;transform:scale(.85)}to{opacity:1;transform:scale(1)}}`}</style>
      <div
        role="dialog"
        aria-modal="true"
        style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.75)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 300, padding: '16px', boxSizing: 'border-box',
        }}
        onClick={(e) => { if (e.target === e.currentTarget && !cancelling) onClose() }}
      >
        <div
          style={{
            background: 'var(--bg-card-product, #1a1a1a)',
            borderRadius: '16px',
            border: '1px solid rgba(255,255,255,0.08)',
            width: '100%', maxWidth: 'min(430px, 100%)', maxHeight: '80vh',
            display: 'flex', flexDirection: 'column',
            overflow: 'hidden',
            animation: '_lrModalIn 200ms ease-out forwards',
          }}
        >
          {/* ── Scrollable body ──────────────────────────── */}
          <div style={{ overflowY: 'auto', padding: '20px 20px 0', flex: 1, boxSizing: 'border-box', minWidth: 0 }}>
            {isDelivery && order.deliveryAddress && (
              <p style={{ fontSize: '13px', color: 'rgba(255,255,255,0.6)', marginBottom: '16px', marginTop: 0 }}>
                {order.deliveryAddress}
              </p>
            )}

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

            <div className={styles.panelSeparator} />

            <p className={styles.sectionLabel}>TU PEDIDO</p>
            {itemsLoading && <ItemsSkeleton />}
            {itemsError && <p className={styles.itemsError}>No se pudieron cargar los items.</p>}
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

            <div style={{ height: '20px' }} />
          </div>

          {/* ── Footer ───────────────────────────────────── */}
          <div style={{
            padding: '16px 20px',
            borderTop: '1px solid rgba(255,255,255,0.08)',
            flexShrink: 0,
            boxSizing: 'border-box',
          }}>
            {confirmingCancel ? (
              <>
                <p style={{
                  fontSize: '13px',
                  color: 'rgba(255,255,255,0.7)',
                  margin: '0 0 10px',
                  lineHeight: 1.45,
                }}>
                  {canDirectCancel
                    ? 'El pedido será cancelado y no podrá reactivarse.'
                    : 'Se enviará una solicitud al local. La decisión final queda a cargo del local.'}
                </p>
                <textarea
                  className={styles.cancelReasonInput}
                  value={cancelReason}
                  onChange={onReasonChange}
                  placeholder={canRequestCancel ? 'Motivo (obligatorio)' : 'Motivo (opcional)'}
                  rows={3}
                  disabled={cancelling}
                />
                {cancelError && (
                  <p style={{ fontSize: '13px', color: 'var(--color-danger, #f87171)', margin: '0 0 12px' }}>
                    {cancelError}
                  </p>
                )}
                <div className={styles.modalActions}>
                  <button className={styles.modalBtnDismiss} onClick={onCancelBack} disabled={cancelling}>
                    Volver
                  </button>
                  <button
                    className={styles.modalBtnConfirm}
                    onClick={onConfirmCancel}
                    disabled={cancelling || (canRequestCancel && !cancelReason.trim())}
                  >
                    {cancelling
                      ? 'Procesando...'
                      : canDirectCancel ? 'Confirmar cancelación' : 'Confirmar solicitud'}
                  </button>
                </div>
              </>
            ) : (
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  style={{
                    flex: 1,
                    fontFamily: "'Barlow Condensed', sans-serif",
                    fontWeight: 700,
                    fontSize: '14px',
                    letterSpacing: '0.06em',
                    color: 'rgba(255,255,255,0.65)',
                    background: 'transparent',
                    border: '1px solid rgba(255,255,255,0.2)',
                    borderRadius: '8px',
                    padding: '10px',
                    cursor: 'pointer',
                  }}
                  onClick={onClose}
                >
                  Cerrar
                </button>
                {(canDirectCancel || canRequestCancel) && (
                  <button
                    style={{ flex: 1 }}
                    className={styles.cancelBtn}
                    onClick={onRequestCancel}
                  >
                    {canRequestCancel ? 'Solicitar cancelación' : 'Cancelar pedido'}
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </>,
    document.body
  )
}

// ── OrderSlide ────────────────────────────────────────────────

function OrderSlide({ orderId, order, estimatedDeliveryMinutes, onPhoneClick, onOrderUpdate, onModalChange, onDismiss }) {
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [reasonModalOpen, setReasonModalOpen] = useState(false)

  const [items, setItems] = useState(null)
  const [itemsLoading, setItemsLoading] = useState(false)
  const [itemsError, setItemsError] = useState(false)
  const fetchedRef = useRef(false)

  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState(null)

  const canDirectCancel = order?.status === 'RECEIVED' || order?.status === 'PENDING_PAYMENT'
  const canRequestCancel = order?.status === 'IN_PREPARATION'

  const fetchItems = useCallback(async () => {
    if (fetchedRef.current) return
    fetchedRef.current = true
    setItemsLoading(true)
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/items`)
      if (!res.ok) throw new Error()
      setItems(await res.json())
    } catch {
      setItemsError(true)
      fetchedRef.current = false
    } finally {
      setItemsLoading(false)
    }
  }, [orderId])

  const handleOpenDetailModal = () => {
    setIsModalOpen(true)
    onModalChange(true)
    fetchItems()
  }

  const handleCloseModal = () => {
    setIsModalOpen(false)
    setConfirmingCancel(false)
    setCancelError(null)
    setCancelReason('')
    onModalChange(false)
  }

  const handleRequestCancel = () => {
    setConfirmingCancel(true)
    setCancelError(null)
  }

  const handleCancelBack = () => {
    setConfirmingCancel(false)
    setCancelError(null)
    setCancelReason('')
  }

  const handleConfirmCancel = async () => {
    setCancelling(true)
    setCancelError(null)
    try {
      const reason = cancelReason.trim() || null
      await cancelOrder(orderId, reason)
      setIsModalOpen(false)
      setConfirmingCancel(false)
      setCancelReason('')
      onModalChange(false)
      onOrderUpdate(orderId, canDirectCancel ? 'CANCELLED' : 'CANCELLATION_REQUESTED', reason)
    } catch (err) {
      setCancelError(err.status === 422 ? err.message : 'No se pudo procesar la solicitud.')
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

  if (order.status === 'CANCELLED') {
    return (
      <>
        {reasonModalOpen && (
          <CancellationReasonModal
            reason={order.cancellationReason}
            onClose={() => {
              setReasonModalOpen(false)
              onModalChange(false)
              onDismiss(orderId)
            }}
          />
        )}
        <div className={styles.slideContent}>
          <div className={styles.cancelledRow}>
            <span className={styles.title} style={{ fontSize: '20px' }}>Pedido cancelado</span>
            <span className={styles.badge} data-status="CANCELLED">CANCELADO</span>
          </div>
          {order.cancelledByStaff ? (
            <button
              className={styles.viewReasonBtn}
              onClick={() => { setReasonModalOpen(true); onModalChange(true) }}
            >
              Ver motivo de cancelación
            </button>
          ) : (
            <button
              className={styles.acknowledgeBtn}
              onClick={() => onDismiss(orderId)}
            >
              Entendido
            </button>
          )}
        </div>
      </>
    )
  }

  const progress = PROGRESS[order.status] ?? 10
  const isPendingPayment = order.status === 'PENDING_PAYMENT'
  const isDelivery = order.orderType === 'DELIVERY'

  return (
    <>
      {isModalOpen && (
        <OrderDetailModal
          order={order}
          items={items}
          itemsLoading={itemsLoading}
          itemsError={itemsError}
          confirmingCancel={confirmingCancel}
          cancelling={cancelling}
          cancelError={cancelError}
          canDirectCancel={canDirectCancel}
          canRequestCancel={canRequestCancel}
          cancelReason={cancelReason}
          onReasonChange={(e) => setCancelReason(e.target.value)}
          onClose={handleCloseModal}
          onRequestCancel={handleRequestCancel}
          onConfirmCancel={handleConfirmCancel}
          onCancelBack={handleCancelBack}
        />
      )}
      <div className={styles.slideContent}>
        <div className={styles.topRow}>
          <div className={styles.titleBlock}>
            <span className={styles.title}>
              {isPendingPayment ? 'Pago en proceso' : 'Pedido en proceso'}
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

        {/* stopPropagation on touch prevents ghost-click on portal modal backdrop */}
        <button
          className={styles.expandBtn}
          onClick={handleOpenDetailModal}
          onTouchStart={(e) => e.stopPropagation()}
          onTouchEnd={(e) => e.stopPropagation()}
        >
          Ver detalle
        </button>
      </div>
    </>
  )
}

// ── OrderTrackingBanner ───────────────────────────────────────

export function OrderTrackingBanner({ branchId }) {
  const { estimatedDeliveryMinutes, phone } = usePreferredBranch()

  const [orderEntries, setOrderEntries] = useState(() => readActiveOrders())
  const [ordersData, setOrdersData] = useState({})
  const [activeIndex, setActiveIndex] = useState(0)

  const orderEntriesRef = useRef(orderEntries)
  const touchStartRef = useRef(null)
  const visibleEntriesRef = useRef([])
  const clampedIndexRef = useRef(0)
  const anyModalOpenRef = useRef(false)

  const visibleEntries = orderEntries.filter(e => e.branchId === branchId)
  const n = visibleEntries.length
  const clampedIndex = Math.min(activeIndex, Math.max(0, n - 1))

  // Keep refs in sync after every render
  useEffect(() => {
    orderEntriesRef.current = orderEntries
    visibleEntriesRef.current = visibleEntries
    clampedIndexRef.current = clampedIndex
  })

  const reloadOrders = useCallback(() => {
    const fresh = readActiveOrders()
    const freshIds = new Set(fresh.map(e => e.orderId))
    setOrderEntries(prev => {
      const added = fresh.filter(e => !prev.some(p => p.orderId === e.orderId))
      const kept = prev.filter(e => freshIds.has(e.orderId))
      if (added.length === 0 && kept.length === prev.length) return prev
      return [...kept, ...added]
    })
    setOrdersData(prev => {
      const removed = Object.keys(prev).filter(id => !freshIds.has(id))
      if (removed.length === 0) return prev
      const next = { ...prev }
      removed.forEach(id => delete next[id])
      return next
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

          if (data.status === 'DELIVERED') {
            removeActiveOrder(orderId)
            setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
            setOrdersData(prev => {
              const next = { ...prev }
              delete next[orderId]
              return next
            })
          } else {
            let cancellationReason = null
            let cancelledByStaff = false
            if (data.status === 'CANCELLED') {
              const hasCancellationRequested = data.history?.some(
                h => h.toStatus === 'CANCELLATION_REQUESTED'
              )
              const cancelledEntry = data.history?.find(h => h.toStatus === 'CANCELLED')
              cancelledByStaff = !hasCancellationRequested && (cancelledEntry?.cancelledByStaff ?? false)
              cancellationReason = cancelledByStaff ? (cancelledEntry?.cancellationReason ?? null) : null
            }
            setOrdersData(prev => ({ ...prev, [orderId]: { ...data, cancellationReason, cancelledByStaff } }))
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
    if (anyModalOpenRef.current) return
    touchStartRef.current = e.touches[0].clientX
  }

  const handleTouchEnd = (e) => {
    if (anyModalOpenRef.current || touchStartRef.current === null) return
    const delta = e.changedTouches[0].clientX - touchStartRef.current
    if (delta < -50) {
      setActiveIndex(i => Math.min(n - 1, i + 1))
    } else if (delta > 50) {
      setActiveIndex(i => Math.max(0, i - 1))
    }
    touchStartRef.current = null
  }

  const handleModalChange = useCallback((isOpen) => {
    anyModalOpenRef.current = isOpen
  }, [])

  const handleDismissOrder = useCallback((orderId) => {
    removeActiveOrder(orderId)
    setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
    setOrdersData(prev => {
      const next = { ...prev }
      delete next[orderId]
      return next
    })
  }, [])

  const handleOrderUpdate = useCallback((orderId, newStatus, reason = null) => {
    if (newStatus === 'DELIVERED') {
      removeActiveOrder(orderId)
      setOrderEntries(prev => prev.filter(e => e.orderId !== orderId))
      setOrdersData(prev => {
        const next = { ...prev }
        delete next[orderId]
        return next
      })
    } else if (newStatus === 'CANCELLED') {
      setOrdersData(prev => ({
        ...prev,
        [orderId]: { ...(prev[orderId] ?? {}), status: 'CANCELLED', cancellationReason: reason, cancelledByStaff: false },
      }))
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
                  estimatedDeliveryMinutes={estimatedDeliveryMinutes}
                  onPhoneClick={handlePhoneClick}
                  onOrderUpdate={handleOrderUpdate}
                  onModalChange={handleModalChange}
                  onDismiss={handleDismissOrder}
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
