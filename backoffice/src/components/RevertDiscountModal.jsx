import { useState } from 'react'
import ReactDOM from 'react-dom'
import CustomSelect from './CustomSelect'
import { revertDiscount } from '../services/ordersService'
import { DISCOUNT_REASON_OPTIONS, discountErrorMessage } from '../utils/ordersUtils'
import './DiscountModal.css'

function formatAmount(n) {
  return '$' + Number(n).toLocaleString('es-AR', { maximumFractionDigits: 2 })
}

/**
 * Modal para borrar (revertir) el descuento vigente de un pedido (US-19-06).
 *
 * No es un borrado sin registro: pide un motivo (mismo dropdown de DiscountReason
 * que aplicar) y admite una nota. Al confirmar, el pedido vuelve a su total sin
 * descontar; el backend deja la traza aplicado -> revertido en la tabla.
 *
 * Reusa el shell y los estilos de DiscountModal (clases dsm-*) y CustomSelect, sin
 * el input de porcentaje ni el preview de cálculo — revertir siempre deja el total
 * completo, así que sólo se muestra a cuánto vuelve el pedido.
 */
export default function RevertDiscountModal({ order, token, branchId, onClose, onReverted }) {
  const [reason, setReason] = useState('OTHER')
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // El total sin descuento = subtotal + fees, la misma base que usa el backend.
  const restoredTotal =
    Number(order.subtotal ?? 0) + Number(order.deliveryFee ?? 0) + Number(order.serviceFee ?? 0)

  async function handleSubmit(e) {
    e.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await revertDiscount(order.id, { reason, note }, token, branchId)
      window.dispatchEvent(
        new CustomEvent('pedisur:toast', { detail: { message: 'Descuento eliminado' } }),
      )
      onReverted()
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
        aria-labelledby="dsm-revert-title"
      >
        <div className="dsm-header">
          <h2 className="dsm-title" id="dsm-revert-title">Borrar descuento</h2>
          <button type="button" className="dsm-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="dsm-form" onSubmit={handleSubmit}>
          <p className="dsm-revert-lead">
            El descuento se quita y el pedido vuelve a <strong>{formatAmount(restoredTotal)}</strong>.
            La traza del descuento anterior queda registrada.
          </p>

          <div className="dsm-field">
            <span className="dsm-label" id="dsm-revert-reason-label">Motivo</span>
            <CustomSelect
              id="dsm-revert-reason"
              value={reason}
              onChange={setReason}
              options={DISCOUNT_REASON_OPTIONS}
              ariaLabelledBy="dsm-revert-reason-label"
            />
          </div>

          <div className="dsm-field">
            <label className="dsm-label" htmlFor="dsm-revert-note">Nota (opcional)</label>
            <textarea
              id="dsm-revert-note"
              className="dsm-textarea"
              rows={2}
              maxLength={500}
              placeholder="Motivo del cambio"
              value={note}
              onChange={e => setNote(e.target.value)}
            />
          </div>

          {error && <p className="dsm-error" role="alert">{error}</p>}

          <div className="dsm-actions">
            <button type="button" className="dsm-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="dsm-submit dsm-submit--danger" disabled={submitting}>
              {submitting ? 'Borrando…' : 'Borrar descuento'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
