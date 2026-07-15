import { useState } from 'react'
import { useShift } from '../context/ShiftContext'
import { useOrdersContext } from '../context/OrdersContext'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import useOperatorMessages from '../hooks/useOperatorMessages'
import { downloadShiftSummary } from '../services/ticketService'
import {
  formatDuration,
  formatCurrency,
  formatShiftClock,
  formatShiftDate,
  getInitials,
  percentOf,
} from '../utils/shiftsUtils'
import './Summary.css'

/* ── Iconos de desglose (currentColor, heredan el color de la fila) ── */
const CashIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="2" y="6" width="20" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
    <circle cx="12" cy="12" r="2.5" stroke="currentColor" strokeWidth="1.8" />
  </svg>
)
const CardIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
    <rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
  </svg>
)
const QrIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="3" y="3" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.8" />
    <rect x="15" y="3" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.8" />
    <rect x="3" y="15" width="6" height="6" rx="1" stroke="currentColor" strokeWidth="1.8" />
    <path d="M15 15h3v3M21 15v6h-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)
const ClockIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8" />
    <path d="M12 7v5l3 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)
const DownloadIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    <polyline points="7 10 12 15 17 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M12 15V3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const REVENUE_ROWS = [
  { key: 'cashRevenue', label: 'Efectivo', mod: 'is-cash', icon: <CashIcon /> },
  { key: 'mpRevenue', label: 'MercadoPago', mod: 'is-mp', icon: <CardIcon /> },
  { key: 'qrRevenue', label: 'QR', mod: 'is-qr', icon: <QrIcon /> },
]

function BreakdownRow({ label, icon, amount, value, total, mod, format }) {
  const pct = percentOf(value ?? amount, total)
  return (
    <div className={`summary-breakdown-row ${mod}`}>
      <span className="summary-breakdown-label">
        {icon && <span className="summary-breakdown-icon">{icon}</span>}
        {label}
      </span>
      <div className="summary-bar-track">
        <div className="summary-bar-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="summary-breakdown-amount">{format ? format(amount) : amount}</span>
      <span className="summary-breakdown-pct">{pct}%</span>
    </div>
  )
}

function SummaryContent({ state }) {
  const { mode, label, openedAt, openedBy, closedAt, summary } = state
  const isActive = mode === 'active'

  return (
    <>
      {/* ── Card superior: encargado + período ─────────────────── */}
      <section className="summary-period-card">
        <div className="summary-manager">
          <div className="summary-avatar" aria-hidden="true">{getInitials(openedBy)}</div>
          <div className="summary-manager-info">
            <span className={`summary-status-line ${isActive ? 'is-active' : 'is-closed'}`}>
              <span className="summary-status-dot" />
              {label}
            </span>
            <span className="summary-manager-name">{openedBy ?? '—'}</span>
            <span className="summary-manager-sub">Encargado · {formatShiftDate(openedAt)}</span>
          </div>
        </div>

        <div className="summary-period-meta">
          <div className="summary-period-block">
            <span className="summary-period-caption">PERÍODO</span>
            <div className="summary-period-range">
              <span>{formatShiftClock(openedAt)}</span>
              <span className="summary-period-arrow">→</span>
              <span className={isActive ? 'summary-period-now' : ''}>
                {isActive ? 'en curso' : formatShiftClock(closedAt)}
              </span>
            </div>
          </div>
          <span className="summary-duration-pill">
            <ClockIcon />
            {formatDuration(openedAt, closedAt)}
          </span>
        </div>
      </section>

      {/* ── Grid de métricas ───────────────────────────────────── */}
      <section className="summary-metrics">
        <div className="summary-metric summary-metric--success">
          <span className="summary-metric-label">Pedidos entregados</span>
          <span className="summary-metric-value">{summary.deliveredOrders ?? 0}</span>
        </div>
        <div className="summary-metric summary-metric--danger">
          <span className="summary-metric-label">Pedidos cancelados</span>
          <span className="summary-metric-value">{summary.cancelledOrders ?? 0}</span>
        </div>
        <div className="summary-metric summary-metric--revenue">
          <span className="summary-metric-label">Ingresos totales</span>
          <span className="summary-metric-value">{formatCurrency(summary.totalRevenue)}</span>
        </div>
        <div className="summary-metric summary-metric--ticket">
          <span className="summary-metric-label">Ticket promedio</span>
          <span className="summary-metric-value">{formatCurrency(summary.averageTicket)}</span>
        </div>
      </section>

      {/* ── Desglose de ingresos ───────────────────────────────── */}
      <section className="summary-breakdown">
        <div className="summary-breakdown-header">
          <h2 className="summary-breakdown-title">Desglose de ingresos</h2>
          <span className="summary-breakdown-total">Total {formatCurrency(summary.totalRevenue)}</span>
        </div>
        {REVENUE_ROWS.map(({ key, label: rowLabel, mod, icon }) => (
          <BreakdownRow
            key={key}
            label={rowLabel}
            icon={icon}
            amount={summary[key]}
            total={summary.totalRevenue}
            mod={mod}
            format={formatCurrency}
          />
        ))}
      </section>

      {/* ── Desglose de modalidad ──────────────────────────────── */}
      <section className="summary-breakdown">
        <div className="summary-breakdown-header">
          <h2 className="summary-breakdown-title">Modalidad de entrega</h2>
          <span className="summary-breakdown-total">{summary.deliveredOrders ?? 0} entregados</span>
        </div>
        <BreakdownRow
          label="Delivery"
          mod="is-cash"
          amount={summary.deliveryOrders ?? 0}
          total={summary.deliveredOrders}
        />
        <BreakdownRow
          label="Takeaway"
          mod="is-qr"
          amount={summary.takeawayOrders ?? 0}
          total={summary.deliveredOrders}
        />
      </section>
    </>
  )
}

export default function Summary() {
  // El resumen vive en ShiftProvider: persiste entre navegaciones, por lo que al
  // volver a esta pestaña los datos se muestran de inmediato sin spinner.
  const { shift, closeShift, summary } = useShift()
  const { state, loading, error } = summary
  const { orders, clearOrders } = useOrdersContext()
  const { tenantName } = useAuth()
  const { activeBranchName } = useBranch()
  const { addMessage } = useOperatorMessages()

  const [confirmOpen, setConfirmOpen] = useState(false)
  const [closing, setClosing] = useState(false)
  const [closeError, setCloseError] = useState(null)

  async function handleConfirmClose() {
    setClosing(true)
    setCloseError(null)
    try {
      // Reutiliza la lógica compartida de cierre. Al resolver, el turno compartido
      // pasa a null: el sub-header se actualiza y el resumen del ShiftProvider
      // recarga solo (cambia openedAt), reemplazando las métricas en vivo por el
      // último turno cerrado (o empty state).
      await closeShift()
      setConfirmOpen(false)
    } catch (err) {
      // 422 por pedidos activos / recepción habilitada: mostramos el mensaje del
      // backend en el modal y no cerramos.
      const msg = err?.message ?? 'No se pudo cerrar el turno.'
      setCloseError(msg)
      if (err?.status === 422 && msg.toLowerCase().includes('pedidos activos')) {
        addMessage({ type: 'danger', text: msg })
      }
    } finally {
      setClosing(false)
    }
  }

  // Descarga el resumen del turno mostrado (US-16-05). Sirve tanto para el turno
  // en curso (Informe X: closedAt null → el PDF dice "En curso") como para el
  // último turno cerrado (Informe Z). Usa el `state` ya cargado, sin fetch nuevo.
  async function handleDownloadSummary() {
    try {
      await downloadShiftSummary(state, { name: activeBranchName, tenantName })
    } catch {
      /* silent — apiFetch/PDF ya no aplican acá; fallo de render se ignora */
    }
  }

  // Solo se puede limpiar la lista entre turnos (sin turno activo) y si hay algo
  // que limpiar.
  const clearDisabled = shift !== null || orders.length === 0
  // Además de descartar el empty state, exigimos que `summary` esté presente:
  // un turno CLOSED sin summary persistido (histórico de auto-cierres viejos)
  // llegaba con mode='closed' pero summary null y hacía crashear SummaryContent
  // al leer summary.deliveredOrders → pantalla en negro. Con esta guarda cae al
  // empty state en su lugar.
  const hasData = state && state.mode !== 'empty' && state.summary != null

  return (
    <div className="summary-page">
      <header className="summary-header">
        <div className="summary-header-text">
          <h1 className="summary-title">Resumen</h1>
          {hasData && (
            <p className="summary-subtitle">
              {state.mode === 'active' ? 'Turno en curso · abierto a las ' : 'Último turno · abierto a las '}
              <strong>{formatShiftClock(state.openedAt)}</strong>
            </p>
          )}
        </div>
        <div className="summary-header-actions">
          {hasData && (
            <button
              className="summary-download-btn"
              type="button"
              onClick={handleDownloadSummary}
              aria-label="Descargar resumen del turno"
              title="Descargar resumen (PDF)"
            >
              <DownloadIcon />
            </button>
          )}
          {shift && (
            <button
              className="summary-close-btn"
              onClick={() => { setCloseError(null); setConfirmOpen(true) }}
            >
              Cerrar turno
            </button>
          )}
          <button
            className="summary-clear-btn"
            onClick={clearOrders}
            disabled={clearDisabled}
          >
            Limpiar lista de pedidos
          </button>
        </div>
      </header>

      {loading ? (
        <div className="summary-state-center">
          <div className="summary-spinner" />
        </div>
      ) : error ? (
        <div className="summary-state-center">
          <p className="summary-state-error">No se pudo cargar el resumen del turno.</p>
        </div>
      ) : !hasData ? (
        <div className="summary-state-center">
          <p className="summary-empty">No hay turnos registrados aún</p>
        </div>
      ) : (
        <SummaryContent state={state} />
      )}

      {/* ── Modal de confirmación de cierre ────────────────────── */}
      {confirmOpen && (
        <div className="summary-overlay" role="dialog" aria-modal="true" aria-labelledby="summary-close-title">
          <div className="summary-modal">
            <p className="summary-modal-title" id="summary-close-title">¿Cerrar el turno?</p>
            <p className="summary-modal-body">Esta acción no se puede deshacer.</p>
            {closeError && <p className="summary-modal-error">{closeError}</p>}
            <div className="summary-modal-actions">
              <button
                className="summary-modal-btn summary-modal-btn--secondary"
                onClick={() => setConfirmOpen(false)}
                disabled={closing}
              >
                Cancelar
              </button>
              <button
                className="summary-modal-btn summary-modal-btn--danger"
                onClick={handleConfirmClose}
                disabled={closing}
              >
                {closing ? 'Cerrando…' : 'Cerrar turno'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
