import { useState } from 'react'
import ReactDOM from 'react-dom'
import CustomSelect from './CustomSelect'
import { applyDiscount } from '../services/ordersService'
import { DISCOUNT_REASON_OPTIONS, discountErrorMessage } from '../utils/ordersUtils'
import './DiscountModal.css'

/** Redondeo HALF_UP a 2 decimales, igual que el backend. Los montos son positivos. */
function roundHalfUp(n) {
  return Math.round((n + Number.EPSILON) * 100) / 100
}

function formatAmount(n) {
  return '$' + Number(n).toLocaleString('es-AR', { maximumFractionDigits: 2 })
}

/**
 * Modal de descuento porcentual manual (US-19-02). El preview del monto final se
 * calcula en el cliente sobre `subtotal + deliveryFee + serviceFee` — la misma
 * base que usa el backend — y no sobre `totalAmount`, que puede venir ya
 * descontado por una aplicación previa. Así el número que ve el operador antes de
 * confirmar es el que queda persistido.
 */
export default function DiscountModal({ order, token, branchId, onClose, onApplied }) {
  const [percentage, setPercentage] = useState('')
  const [reason, setReason] = useState('CUSTOMER_PROMO')
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const subtotal = Number(order.subtotal ?? 0)
  const fees = Number(order.deliveryFee ?? 0) + Number(order.serviceFee ?? 0)
  const originalTotal = subtotal + fees

  const parsed = percentage.trim() === '' ? null : Number(percentage.replace(',', '.'))
  const isValidPercentage =
    parsed != null && Number.isFinite(parsed) && parsed >= 0 && parsed <= 100

  const discountAmount = isValidPercentage ? roundHalfUp((subtotal * parsed) / 100) : 0
  const finalTotal = originalTotal - discountAmount

  const canSubmit = isValidPercentage && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      await applyDiscount(order.id, { percentage: parsed, reason, note }, token, branchId)
      window.dispatchEvent(
        new CustomEvent('laroka:toast', { detail: { message: 'Descuento aplicado' } }),
      )
      onApplied()
      onClose()
    } catch (err) {
      setError(discountErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const modal = (
    <div className="dsm-backdrop" onClick={handleClose}>
      <div
        className="dsm-dialog"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dsm-title"
      >
        <div className="dsm-header">
          <h2 className="dsm-title" id="dsm-title">Aplicar descuento</h2>
          <button type="button" className="dsm-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="dsm-form" onSubmit={handleSubmit}>
          <div className="dsm-field">
            <label className="dsm-label" htmlFor="dsm-percentage">Porcentaje</label>
            <div className="dsm-percentage-wrap">
              <input
                id="dsm-percentage"
                className="dsm-input"
                type="number"
                inputMode="decimal"
                min="0"
                max="100"
                step="0.01"
                placeholder="0"
                value={percentage}
                onChange={e => setPercentage(e.target.value)}
                autoFocus
              />
              <span className="dsm-percentage-sign" aria-hidden="true">%</span>
            </div>
            {percentage.trim() !== '' && !isValidPercentage && (
              <p className="dsm-hint dsm-hint--warn">Ingresá un número entre 0 y 100.</p>
            )}
          </div>

          <div className="dsm-field">
            <span className="dsm-label" id="dsm-reason-label">Motivo</span>
            <CustomSelect
              id="dsm-reason"
              value={reason}
              onChange={setReason}
              options={DISCOUNT_REASON_OPTIONS}
              ariaLabelledBy="dsm-reason-label"
            />
          </div>

          <div className="dsm-field">
            <label className="dsm-label" htmlFor="dsm-note">Nota (opcional)</label>
            <textarea
              id="dsm-note"
              className="dsm-textarea"
              rows={2}
              maxLength={500}
              placeholder="Detalle del ajuste"
              value={note}
              onChange={e => setNote(e.target.value)}
            />
          </div>

          {/* Preview en vivo: el descuento se calcula sobre el subtotal, los fees
              se cobran enteros. */}
          <div className="dsm-preview">
            <div className="dsm-preview-row">
              <span>Subtotal</span>
              <span>{formatAmount(subtotal)}</span>
            </div>
            {fees > 0 && (
              <div className="dsm-preview-row">
                <span>Envío y servicio</span>
                <span>{formatAmount(fees)}</span>
              </div>
            )}
            <div className="dsm-preview-row dsm-preview-row--discount">
              <span>Descuento</span>
              <span data-testid="dsm-discount-amount">−{formatAmount(discountAmount)}</span>
            </div>
            <div className="dsm-preview-row dsm-preview-row--total">
              <span>Total con descuento</span>
              <span data-testid="dsm-final-total">{formatAmount(finalTotal)}</span>
            </div>
          </div>

          {error && <p className="dsm-error" role="alert">{error}</p>}

          <div className="dsm-actions">
            <button type="button" className="dsm-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="dsm-submit" disabled={!canSubmit}>
              {submitting ? 'Aplicando…' : 'Aplicar descuento'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
