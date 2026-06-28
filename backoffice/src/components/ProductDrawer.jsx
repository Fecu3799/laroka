import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createProduct, updateProduct } from '../services/catalogService'
import CustomSelect from './CustomSelect'
import './StaffUserDrawer.css'

const EMPTY_FORM = { name: '', description: '', categoryId: '', price: '', imageUrl: '' }

export default function ProductDrawer({ open, mode, product, categories, onClose, onSaved }) {
  const { token, tenantId } = useAuth()
  const isEdit = mode === 'edit'

  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    if (isEdit && product) {
      setForm({
        name: product.name ?? '',
        description: product.description ?? '',
        categoryId: product.categoryId != null ? String(product.categoryId) : '',
        price: product.price != null ? String(product.price) : '',
        imageUrl: product.imageUrl ?? '',
      })
    } else {
      setForm(EMPTY_FORM)
    }
  }, [open, isEdit, product])

  function setField(key, value) {
    setForm(prev => ({ ...prev, [key]: value }))
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const nameValid = form.name.trim().length > 0
  const categoryValid = form.categoryId !== ''
  const priceNumber = Number(form.price)
  const priceValid = form.price !== '' && Number.isFinite(priceNumber) && priceNumber > 0
  const canSubmit = nameValid && categoryValid && priceValid && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        price: priceNumber,
        imageUrl: form.imageUrl.trim() || null,
        categoryId: Number(form.categoryId),
        tenantId,
      }
      if (isEdit) {
        await updateProduct(product.id, payload, token)
      } else {
        await createProduct(payload, token)
      }
      onSaved()
      onClose()
    } catch (err) {
      // apiFetch ya emite un toast con el mensaje del backend; lo repetimos inline
      // junto al formulario para que quede visible en el drawer.
      setError(err?.message ?? 'No se pudo guardar el producto.')
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
        aria-label={isEdit ? 'Editar producto' : 'Nuevo producto'}
      >
        <div className="sud-header">
          <h2 className="sud-title">{isEdit ? 'Editar producto' : 'Nuevo producto'}</h2>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="sud-form" onSubmit={handleSubmit}>
          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-name">Nombre</label>
            <input
              id="prod-name"
              className="sud-input"
              type="text"
              placeholder="Nombre del producto"
              value={form.name}
              onChange={e => setField('name', e.target.value)}
              autoFocus
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-description">Descripción</label>
            <textarea
              id="prod-description"
              className="sud-input"
              rows={3}
              placeholder="Descripción (opcional)"
              value={form.description}
              onChange={e => setField('description', e.target.value)}
              style={{ resize: 'vertical', minHeight: '78px', fontFamily: 'inherit', lineHeight: 1.5 }}
            />
          </div>

          <div className="sud-field">
            <span className="sud-label" id="prod-category-label">Categoría</span>
            <CustomSelect
              id="prod-category"
              ariaLabelledBy="prod-category-label"
              value={form.categoryId}
              onChange={val => setField('categoryId', val)}
              options={categories.map(c => ({ value: String(c.id), label: c.name }))}
              placeholder="Seleccionar categoría…"
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-price">Precio base</label>
            <input
              id="prod-price"
              className="sud-input"
              type="number"
              min="0"
              step="0.01"
              placeholder="0"
              value={form.price}
              onChange={e => setField('price', e.target.value)}
            />
            {form.price !== '' && !priceValid && (
              <span className="sud-hint">El precio debe ser un número mayor a 0.</span>
            )}
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-image">URL de imagen</label>
            <input
              id="prod-image"
              className="sud-input"
              type="text"
              placeholder="https://… (opcional)"
              value={form.imageUrl}
              onChange={e => setField('imageUrl', e.target.value)}
            />
          </div>

          {error && <p className="sud-error">{error}</p>}

          <div className="sud-actions">
            <button type="button" className="sud-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="sud-submit" disabled={!canSubmit}>
              {submitting ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear producto'}
            </button>
          </div>
        </form>
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
