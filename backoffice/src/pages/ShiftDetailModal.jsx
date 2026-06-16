import { useEffect } from 'react'
import {
  formatDuration,
  formatCurrency,
  formatShiftClock,
  formatShiftDate,
  getInitials,
  percentOf,
} from '../utils/shiftsUtils'
import './ShiftDetailModal.css'

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

const REVENUE_ROWS = [
  { key: 'cashRevenue', label: 'Efectivo', mod: 'is-cash', icon: <CashIcon /> },
  { key: 'mpRevenue', label: 'MercadoPago', mod: 'is-mp', icon: <CardIcon /> },
  { key: 'qrRevenue', label: 'QR', mod: 'is-qr', icon: <QrIcon /> },
]

function BreakdownRow({ label, icon, amount, total, mod }) {
  const pct = percentOf(amount, total)
  return (
    <div className={`sdm-breakdown-row ${mod}`}>
      <span className="sdm-breakdown-label">
        {icon && <span className="sdm-breakdown-icon">{icon}</span>}
        {label}
      </span>
      <div className="sdm-bar-track">
        <div className="sdm-bar-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="sdm-breakdown-amount">{formatCurrency(amount)}</span>
      <span className="sdm-breakdown-pct">{pct}%</span>
    </div>
  )
}

function shiftIdSuffix(id) {
  if (!id) return '—'
  return id.replace(/-/g, '').slice(-4).toUpperCase()
}

export default function ShiftDetailModal({ shift, onClose }) {
  const { shiftId, openedAt, closedAt, openedBy, summary } = shift

  useEffect(() => {
    function onKey(e) { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="sdm-overlay"
      role="dialog"
      aria-modal="true"
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div className="sdm-modal">
        {/* ── Header ─────────────────────────────────────────────── */}
        <div className="sdm-modal-header">
          <div className="sdm-modal-title-group">
            <span className="sdm-clock-icon" aria-hidden="true"><ClockIcon /></span>
            <div>
              <h2 className="sdm-modal-title">Turno #{shiftIdSuffix(shiftId)}</h2>
              <p className="sdm-modal-date">{formatShiftDate(openedAt)}</p>
            </div>
          </div>
          <button className="sdm-close-btn" onClick={onClose} aria-label="Cerrar">×</button>
        </div>

        {/* ── Period card ─────────────────────────────────────────── */}
        <section className="sdm-period-card">
          <div className="sdm-manager">
            <div className="sdm-avatar" aria-hidden="true">{getInitials(openedBy)}</div>
            <div className="sdm-manager-info">
              <span className="sdm-status-line is-closed">
                <span className="sdm-status-dot" />
                CERRADO
              </span>
              <span className="sdm-manager-name">{openedBy ?? '—'}</span>
              <span className="sdm-manager-sub">Encargado · {formatShiftDate(openedAt)}</span>
            </div>
          </div>
          <div className="sdm-period-meta">
            <div className="sdm-period-block">
              <span className="sdm-period-caption">PERÍODO</span>
              <div className="sdm-period-range">
                <span>{formatShiftClock(openedAt)}</span>
                <span className="sdm-period-arrow">→</span>
                <span>{formatShiftClock(closedAt)}</span>
              </div>
            </div>
            <span className="sdm-duration-pill">
              <ClockIcon />
              {formatDuration(openedAt, closedAt)}
            </span>
          </div>
        </section>

        {/* ── Metrics 2x2 ────────────────────────────────────────── */}
        <section className="sdm-metrics">
          <div className="sdm-metric sdm-metric--success">
            <span className="sdm-metric-label">Pedidos entregados</span>
            <span className="sdm-metric-value">{summary?.deliveredOrders ?? 0}</span>
          </div>
          <div className="sdm-metric sdm-metric--danger">
            <span className="sdm-metric-label">Pedidos cancelados</span>
            <span className="sdm-metric-value">{summary?.cancelledOrders ?? 0}</span>
          </div>
          <div className="sdm-metric sdm-metric--revenue">
            <span className="sdm-metric-label">Ingresos totales</span>
            <span className="sdm-metric-value">{formatCurrency(summary?.totalRevenue)}</span>
          </div>
          <div className="sdm-metric sdm-metric--ticket">
            <span className="sdm-metric-label">Ticket promedio</span>
            <span className="sdm-metric-value">{formatCurrency(summary?.averageTicket)}</span>
          </div>
        </section>

        {/* ── Revenue breakdown ───────────────────────────────────── */}
        <section className="sdm-breakdown">
          <div className="sdm-breakdown-header">
            <h3 className="sdm-breakdown-title">Desglose de ingresos</h3>
            <span className="sdm-breakdown-total">Total {formatCurrency(summary?.totalRevenue)}</span>
          </div>
          {REVENUE_ROWS.map(({ key, label, mod, icon }) => (
            <BreakdownRow
              key={key}
              label={label}
              icon={icon}
              amount={summary?.[key]}
              total={summary?.totalRevenue}
              mod={mod}
            />
          ))}
        </section>
      </div>
    </div>
  )
}
