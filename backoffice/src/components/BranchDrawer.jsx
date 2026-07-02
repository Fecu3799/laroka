import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createBranch, updateBranchConfig } from '../services/branchService'
import ImageUploader from './ui/ImageUploader'
import './StaffUserDrawer.css'

// minutos → horas como string editable ('' si no hay valor cargado).
function minutesToHours(minutes) {
  if (minutes == null) return ''
  return String(minutes / 60)
}

// Número decimal como string editable ('' si null).
function numToStr(value) {
  return value == null ? '' : String(value)
}

const EMPTY_FORM = {
  name: '',
  address: '',
  phone: '',
  imageUrl: '',
  deliveryFee: '',
  serviceFee: '',
  estimatedDeliveryMinutes: '',
  maxShiftHours: '',
}

export default function BranchDrawer({ open, mode, branch, onClose, onSaved }) {
  const { token, tenantId } = useAuth()
  const isEdit = mode === 'edit'

  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    if (isEdit && branch) {
      setForm({
        name: branch.name ?? '',
        address: branch.address ?? '',
        phone: branch.phone ?? '',
        imageUrl: branch.imageUrl ?? '',
        deliveryFee: numToStr(branch.deliveryFee),
        serviceFee: numToStr(branch.serviceFee),
        estimatedDeliveryMinutes: numToStr(branch.estimatedDeliveryMinutes),
        maxShiftHours: minutesToHours(branch.maxShiftDurationMinutes),
      })
    } else {
      setForm(EMPTY_FORM)
    }
  }, [open, isEdit, branch])

  function setField(key, value) {
    setForm(prev => ({ ...prev, [key]: value }))
  }

  const nameValid = form.name.trim().length > 0
  const addressValid = form.address.trim().length > 0
  const phoneValid = form.phone.trim().length > 0
  const deliveryFeeNum = parseFloat(form.deliveryFee)
  const serviceFeeNum = parseFloat(form.serviceFee)
  const etaNum = parseInt(form.estimatedDeliveryMinutes, 10)
  const maxShiftNum = parseFloat(form.maxShiftHours)
  const deliveryFeeValid = Number.isFinite(deliveryFeeNum) && deliveryFeeNum >= 0
  const serviceFeeValid = Number.isFinite(serviceFeeNum) && serviceFeeNum >= 0
  const etaValid = Number.isInteger(etaNum) && etaNum >= 1
  const maxShiftValid = Number.isFinite(maxShiftNum) && maxShiftNum >= 1

  const canSubmit =
    nameValid && addressValid && phoneValid && deliveryFeeValid &&
    serviceFeeValid && etaValid && (!isEdit || maxShiftValid) && !submitting

  function handleClose() {
    if (submitting) return
    onClose()
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      if (isEdit) {
        await updateBranchConfig(branch.id, {
          maxShiftDurationMinutes: Math.round(maxShiftNum * 60),
          name: form.name.trim(),
          address: form.address.trim(),
          phone: form.phone.trim(),
          imageUrl: form.imageUrl || null,
          deliveryFee: deliveryFeeNum,
          serviceFee: serviceFeeNum,
          estimatedDeliveryMinutes: etaNum,
        }, token)
      } else {
        await createBranch({
          name: form.name.trim(),
          address: form.address.trim(),
          phone: form.phone.trim(),
          imageUrl: form.imageUrl || null,
          deliveryFee: deliveryFeeNum,
          serviceFee: serviceFeeNum,
          estimatedDeliveryMinutes: etaNum,
          tenantId,
        }, token)
      }
      onSaved()
      onClose()
    } catch (err) {
      // apiFetch ya emite un toast con el mensaje del backend; lo repetimos inline.
      setError(err?.message ?? 'No se pudo guardar la sucursal.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!open) return null

  const drawer = (
    <div className="sud-backdrop" onClick={handleClose}>
      <aside
        className="sud-panel"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? 'Editar sucursal' : 'Nueva sucursal'}
      >
        <div className="sud-header">
          <h2 className="sud-title">{isEdit ? 'Editar sucursal' : 'Nueva sucursal'}</h2>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="sud-form" onSubmit={handleSubmit}>
          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-name">Nombre</label>
            <input
              id="bd-name"
              className="sud-input"
              type="text"
              placeholder="Nombre de la sucursal"
              value={form.name}
              onChange={e => setField('name', e.target.value)}
              autoFocus
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-address">Dirección</label>
            <input
              id="bd-address"
              className="sud-input"
              type="text"
              placeholder="Calle y número"
              value={form.address}
              onChange={e => setField('address', e.target.value)}
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-phone">Teléfono</label>
            <input
              id="bd-phone"
              className="sud-input"
              type="tel"
              placeholder="+54 280 ..."
              value={form.phone}
              onChange={e => setField('phone', e.target.value)}
            />
          </div>

          <div className="sud-field">
            <ImageUploader
              label="Imagen"
              value={form.imageUrl || null}
              onChange={url => setField('imageUrl', url)}
              token={token}
              aspectRatio={3.1}
              helperText="Recomendado: imagen horizontal, tipo banner"
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-delivery-fee">Cargo de delivery</label>
            <input
              id="bd-delivery-fee"
              className="sud-input"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              placeholder="0.00"
              value={form.deliveryFee}
              onChange={e => setField('deliveryFee', e.target.value)}
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-service-fee">Cargo de servicio</label>
            <input
              id="bd-service-fee"
              className="sud-input"
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              placeholder="0.00"
              value={form.serviceFee}
              onChange={e => setField('serviceFee', e.target.value)}
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="bd-eta">Tiempo estimado de entrega (min)</label>
            <input
              id="bd-eta"
              className="sud-input"
              type="number"
              min="1"
              step="1"
              inputMode="numeric"
              placeholder="30"
              value={form.estimatedDeliveryMinutes}
              onChange={e => setField('estimatedDeliveryMinutes', e.target.value)}
            />
          </div>

          {isEdit && (
            <div className="sud-field">
              <label className="sud-label" htmlFor="bd-max-shift">Duración máxima de turno (horas)</label>
              <input
                id="bd-max-shift"
                className="sud-input"
                type="number"
                min="1"
                step="0.5"
                inputMode="decimal"
                placeholder="12"
                value={form.maxShiftHours}
                onChange={e => setField('maxShiftHours', e.target.value)}
              />
            </div>
          )}

          {error && <p className="sud-error">{error}</p>}

          <div className="sud-actions">
            <button type="button" className="sud-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="sud-submit" disabled={!canSubmit}>
              {submitting ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear sucursal'}
            </button>
          </div>
        </form>
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
