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

// Normaliza el porcentaje vigente para el input: el backend lo persiste como
// NUMERIC(5,2), así que un 10 llega como "10.00" y un 33.33 como "33.33".
// String(Number(x)) descarta los ceros de más sin perder los decimales reales.
function initialPercentage(discount) {
  if (!discount || discount.percentage == null) return ''
  return String(Number(discount.percentage))
}

/**
 * Modal de descuento porcentual manual (US-19-02). El preview del monto final se
 * calcula en el cliente sobre `subtotal + deliveryFee + serviceFee` — la misma
 * base que usa el backend — y no sobre `totalAmount`, que puede venir ya
 * descontado por una aplicación previa. Así el número que ve el operador antes de
 * confirmar es el que queda persistido.
 *
 * US-19-05: con `mode="edit"` el mismo modal sirve para modificar el descuento
 * vigente — se precarga con el porcentaje/motivo/nota de `order.discount` y guarda
 * reaplicando sobre el mismo endpoint (la tabla es append-only, así que "modificar"
 * inserta una fila nueva sin tocar la anterior). No se duplica el componente: sólo
 * cambian los textos y los valores iniciales.
 */
export default function DiscountModal({ order, token, branchId, mode = 'create', onClose, onApplied }) {
  const isEdit = mode === 'edit'
  // El modal se monta al abrir (render condicional en Orders.jsx), así que el
  // estado inicial se calcula una vez desde order.discount, sin necesidad de
  // sincronizar con useEffect.
  const [percentage, setPercentage] = useState(isEdit ? initialPercentage(order.discount) : '')
  const [reason, setReason] = useState(isEdit && order.discount ? order.discount.reason : 'CUSTOMER_PROMO')
  const [note, setNote] = useState(isEdit && order.discount ? (order.discount.note ?? '') : '')
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
        new CustomEvent('pedisur:toast', {
          detail: { message: isEdit ? 'Descuento modificado' : 'Descuento aplicado' },
        }),
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
          <h2 className="dsm-title" id="dsm-title">{isEdit ? 'Modificar descuento' : 'Aplicar descuento'}</h2>
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
              {isEdit
                ? (submitting ? 'Guardando…' : 'Guardar cambios')
                : (submitting ? 'Aplicando…' : 'Aplicar descuento')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
