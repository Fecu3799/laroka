import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import useCurrentShift from '../hooks/useCurrentShift'
import './SubHeader.css'

function fmt(amount) {
  if (amount == null) return '—'
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 }).format(amount)
}

function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
}

export default function SubHeader() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const { activeBranchName, setActiveBranch } = useBranch()
  const { shift, loading, warning, confirmWarning, openShift, closeShift, closeSummary, dismissSummary } = useCurrentShift()
  const [confirmClose, setConfirmClose] = useState(false)

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

          <button className="sub-header-bell" aria-label="Notificaciones" disabled>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <span className="sub-header-bell-badge">0</span>
          </button>
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
