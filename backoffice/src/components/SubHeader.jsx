import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import useCurrentShift from '../hooks/useCurrentShift'
import { fetchOrderDetail } from '../services/ordersService'
import { STATUS_CONFIG } from '../utils/ordersUtils'
import './SubHeader.css'

function fmt(amount) {
  if (amount == null) return '—'
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(amount)
}

function formatFeedTime(date) {
  return date.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

export default function SubHeader() {
  const navigate = useNavigate()
  const { token, role } = useAuth()
  const { activeBranchName, activeBranchId, setActiveBranch } = useBranch()
  const { shift, loading, warning, confirmWarning, openShift, closeShift, closeSummary, dismissSummary } = useCurrentShift()
  const [confirmClose, setConfirmClose] = useState(false)

  // ── Feed state ───────────────────────────────────────────────
  const [feedItems, setFeedItems] = useState([])
  const [panelOpen, setPanelOpen] = useState(false)
  const bellRef = useRef(null)

  // ── Listen to SSE events ─────────────────────────────────────
  useEffect(() => {
    function handle(e) {
      const { orderId, type, order, origin, actionOrigin } = e.detail
      if (!orderId) return
      if (type === 'NEW_ORDER' && origin === 'BACKOFFICE') return
      if (type === 'ORDER_UPDATED' && actionOrigin === 'BACKOFFICE') return
      if (!['NEW_ORDER', 'ORDER_UPDATED', 'CANCELLATION_REQUESTED'].includes(type)) return

      const displayId = orderId.replace(/-/g, '').slice(0, 4).toUpperCase()
      let label = ''
      let color = '#22c55e'

      if (type === 'NEW_ORDER') {
        label = `Nuevo pedido #${displayId} · App Roka`
        color = '#22c55e'
      } else if (type === 'CANCELLATION_REQUESTED') {
        label = `Cancelación solicitada #${displayId}`
        color = '#ef4444'
      } else {
        label = `#${displayId} · ${STATUS_CONFIG[order.status]?.label ?? order.status}`
        color = STATUS_CONFIG[order.status]?.color ?? '#d4e8d6'
      }

      setFeedItems(prev => [{
        id: Math.random().toString(36).slice(2),
        type,
        orderId,
        displayId,
        label,
        color,
        timestamp: new Date(),
        order: order ?? null,
      }, ...prev])
    }

    window.addEventListener('laroka:order-updated', handle)
    return () => window.removeEventListener('laroka:order-updated', handle)
  }, [])

  // ── Clear feed on full refresh ───────────────────────────────
  useEffect(() => {
    function handle() { setFeedItems([]) }
    window.addEventListener('laroka:clear-feed', handle)
    return () => window.removeEventListener('laroka:clear-feed', handle)
  }, [])

  // ── Notify Orders of pending orderIds whenever feed changes ──
  useEffect(() => {
    const orderColorMap = new Map(feedItems.map(item => [item.orderId, item.color]))
    window.dispatchEvent(new CustomEvent('laroka:feed-updated', { detail: { orderColorMap } }))
  }, [feedItems])

  // ── Click outside closes panel ───────────────────────────────
  useEffect(() => {
    if (!panelOpen) return
    function handle(e) {
      if (bellRef.current && !bellRef.current.contains(e.target)) setPanelOpen(false)
    }
    document.addEventListener('mousedown', handle)
    return () => document.removeEventListener('mousedown', handle)
  }, [panelOpen])

  // ── Feed item press ──────────────────────────────────────────
  async function handleFeedItem(item) {
    let order = item.order
    if (item.type !== 'ORDER_UPDATED') {
      try { order = await fetchOrderDetail(item.orderId, token, activeBranchId) } catch { /* noop */ }
    }
    if (order) {
      window.dispatchEvent(new CustomEvent('laroka:order-insert', { detail: { order } }))
    }
    setFeedItems(prev => prev.filter(i => i.id !== item.id))
  }

  async function handleConfirmClose() {
    setConfirmClose(false)
    await closeShift()
  }

  return (
    <>
      <div className="sub-header">
        {/* Branch */}
        <div className="sub-header-branch">
          <span className="sub-header-branch-label">SUCURSAL</span>
          <span className="sub-header-branch-name">{activeBranchName ?? '—'}</span>
          {role === 'ADMIN' && (
            <button
              className="sub-header-branch-change"
              onClick={() => { setActiveBranch(null, null); navigate('/branch-select') }}
              title="Cambiar sucursal"
              aria-label="Cambiar sucursal"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M17 1l4 4-4 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M3 11V9a4 4 0 0 1 4-4h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                <path d="M7 23l-4-4 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M21 13v2a4 4 0 0 1-4 4H3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          )}
        </div>

        {/* Shift + Bell */}
        <div className="sub-header-right">
          <div className="sub-header-shift">
            <span className={`sub-header-shift-dot ${shift ? 'sub-header-shift-dot--open' : 'sub-header-shift-dot--closed'}`} />
            <span className="sub-header-shift-label">{shift ? 'TURNO ABIERTO' : 'TURNO CERRADO'}</span>
            {shift ? (
              <button
                className="sub-header-shift-btn sub-header-shift-btn--close"
                onClick={() => setConfirmClose(true)}
                disabled={loading}
              >
                Cerrar turno
              </button>
            ) : (
              <button
                className="sub-header-shift-btn sub-header-shift-btn--open"
                onClick={openShift}
                disabled={loading}
              >
                Abrir turno
              </button>
            )}
          </div>

          {/* Bell */}
          <div className="sub-header-bell-wrapper" ref={bellRef}>
            <button
              className="sub-header-bell"
              aria-label="Notificaciones"
              onClick={() => setPanelOpen(v => !v)}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <span className={`sub-header-bell-badge${feedItems.length > 0 ? ' sub-header-bell-badge--active' : ''}`}>
                {feedItems.length}
              </span>
            </button>

            {panelOpen && (
              <div className="sub-header-feed-panel">
                <div className="sub-header-feed-header">
                  <span>ACTIVIDAD</span>
                  {feedItems.length > 0 && (
                    <button
                      className="sub-header-feed-clear"
                      onClick={() => setFeedItems([])}
                    >
                      Limpiar todo
                    </button>
                  )}
                </div>
                {feedItems.length === 0 ? (
                  <div className="sub-header-feed-empty">Sin eventos recientes</div>
                ) : (
                  feedItems.map(item => (
                    <button
                      key={item.id}
                      className="sub-header-feed-item"
                      onClick={() => handleFeedItem(item)}
                    >
                      <span className="sub-header-feed-bar" style={{ background: item.color }} />
                      <div className="sub-header-feed-content">
                        <span className="sub-header-feed-label">{item.label}</span>
                        <span className="sub-header-feed-time">{formatFeedTime(item.timestamp)}</span>
                      </div>
                    </button>
                  ))
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Warning: turno anterior cerrado automáticamente */}
      {warning && (
        <div className="sub-header-overlay" role="dialog" aria-modal="true" aria-labelledby="warn-title">
          <div className="sub-header-modal">
            <p className="sub-header-modal-title" id="warn-title">Aviso</p>
            <div className="sub-header-modal-warning">
              El turno anterior fue cerrado automáticamente antes de abrir este nuevo turno.
            </div>
            <div className="sub-header-modal-actions">
              <button className="sub-header-modal-btn sub-header-modal-btn--secondary" onClick={confirmWarning}>
                Entendido
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirmación de cierre */}
      {confirmClose && (
        <div className="sub-header-overlay" role="dialog" aria-modal="true" aria-labelledby="close-title">
          <div className="sub-header-modal">
            <p className="sub-header-modal-title" id="close-title">Cerrar turno</p>
            <p className="sub-header-modal-body">
              ¿Confirmar el cierre del turno? Se calculará el resumen de la jornada.
            </p>
            <div className="sub-header-modal-actions">
              <button
                className="sub-header-modal-btn sub-header-modal-btn--secondary"
                onClick={() => setConfirmClose(false)}
              >
                Cancelar
              </button>
              <button
                className="sub-header-modal-btn sub-header-modal-btn--primary"
                onClick={handleConfirmClose}
                disabled={loading}
              >
                Confirmar cierre
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Resumen post-cierre */}
      {closeSummary && (
        <div className="sub-header-overlay" role="dialog" aria-modal="true" aria-labelledby="summary-title">
          <div className="sub-header-modal">
            <p className="sub-header-modal-title" id="summary-title">Resumen del turno</p>
            <div className="sub-header-summary-grid">
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">Pedidos totales</span>
                <span className="sub-header-summary-val">{closeSummary.totalOrders ?? 0}</span>
              </div>
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">Entregados</span>
                <span className="sub-header-summary-val">{closeSummary.deliveredOrders ?? 0}</span>
              </div>
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">Cancelados</span>
                <span className="sub-header-summary-val">{closeSummary.cancelledOrders ?? 0}</span>
              </div>
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">Ticket promedio</span>
                <span className="sub-header-summary-val">{fmt(closeSummary.averageTicket)}</span>
              </div>
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">Efectivo</span>
                <span className="sub-header-summary-val">{fmt(closeSummary.cashRevenue)}</span>
              </div>
              <div className="sub-header-summary-row">
                <span className="sub-header-summary-key">MercadoPago</span>
                <span className="sub-header-summary-val">{fmt(closeSummary.mpRevenue)}</span>
              </div>
              <div className="sub-header-summary-row" style={{ gridColumn: '1 / -1' }}>
                <span className="sub-header-summary-key">Total recaudado</span>
                <span className="sub-header-summary-val sub-header-summary-val--accent">{fmt(closeSummary.totalRevenue)}</span>
              </div>
            </div>
            <div className="sub-header-modal-actions">
              <button
                className="sub-header-modal-btn sub-header-modal-btn--secondary"
                onClick={dismissSummary}
              >
                Cerrar
              </button>
              <button
                className="sub-header-modal-btn sub-header-modal-btn--accent"
                onClick={() => { dismissSummary(); navigate(`/shifts/summary?shiftId=${closeSummary.shiftId}`) }}
              >
                Ver resumen completo
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
