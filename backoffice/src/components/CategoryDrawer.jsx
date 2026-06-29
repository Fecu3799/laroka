import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createCategory, updateCategory } from '../services/catalogService'
import './StaffUserDrawer.css'

export default function CategoryDrawer({ open, mode, category, onClose, onSaved }) {
  const { token, tenantId } = useAuth()
  const isEdit = mode === 'edit'

  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    setName(isEdit && category ? (category.name ?? '') : '')
  }, [open, isEdit, category])

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const nameValid = name.trim().length > 0
  const canSubmit = nameValid && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      const payload = { name: name.trim(), tenantId }
      if (isEdit) {
        await updateCategory(category.id, payload, token)
      } else {
        await createCategory(payload, token)
      }
      onSaved()
      onClose()
    } catch (err) {
      // apiFetch ya emite un toast con el mensaje del backend; lo repetimos inline
      // junto al formulario para que quede visible en el drawer.
      setError(err?.message ?? 'No se pudo guardar la categoría.')
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
        aria-label={isEdit ? 'Editar categoría' : 'Nueva categoría'}
      >
        <div className="sud-header">
          <h2 className="sud-title">{isEdit ? 'Editar categoría' : 'Nueva categoría'}</h2>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="sud-form" onSubmit={handleSubmit}>
          <div className="sud-field">
            <label className="sud-label" htmlFor="cat-name">Nombre</label>
            <input
              id="cat-name"
              className="sud-input"
              type="text"
              placeholder="Nombre de la categoría"
              value={name}
              onChange={e => setName(e.target.value)}
              autoFocus
            />
          </div>

          {error && <p className="sud-error">{error}</p>}

          <div className="sud-actions">
            <button type="button" className="sud-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="sud-submit" disabled={!canSubmit}>
              {submitting ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear categoría'}
            </button>
          </div>
        </form>
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
