import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createCategory, updateCategory, fetchCategoryTypes } from '../services/catalogService'
import CustomSelect from './CustomSelect'
import './StaffUserDrawer.css'

export default function CategoryDrawer({ open, mode, category, onClose, onSaved }) {
  const { token, tenantId } = useAuth()
  const isEdit = mode === 'edit'

  const [name, setName] = useState('')
  const [categoryTypeId, setCategoryTypeId] = useState('')
  const [types, setTypes] = useState([])
  const [typesError, setTypesError] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    setName(isEdit && category ? (category.name ?? '') : '')
    setCategoryTypeId(isEdit && category?.categoryTypeId != null ? String(category.categoryTypeId) : '')
  }, [open, isEdit, category])

  // US-CAT-03: los tipos maestros activos alimentan el selector. Se cargan al abrir.
  useEffect(() => {
    if (!open || !token) return
    setTypesError(false)
    fetchCategoryTypes(token)
      .then(setTypes)
      .catch(() => setTypesError(true))
  }, [open, token])

  function handleClose() {
    if (submitting) return
    onClose()
  }

  // Al elegir un tipo, el nombre se precarga con el nombre del tipo pero queda editable
  // (US-CAT-03). El ADMIN puede modificarlo antes de guardar.
  function handleTypeChange(val) {
    setCategoryTypeId(val)
    const selected = types.find(t => String(t.id) === String(val))
    if (selected) setName(selected.name)
  }

  const nameValid = name.trim().length > 0
  const typeValid = categoryTypeId !== ''
  const canSubmit = nameValid && typeValid && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      const payload = { name: name.trim(), tenantId, categoryTypeId: Number(categoryTypeId) }
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
            <span className="sud-label" id="cat-type-label">Tipo</span>
            <CustomSelect
              id="cat-type"
              ariaLabelledBy="cat-type-label"
              value={categoryTypeId}
              onChange={handleTypeChange}
              options={types.map(t => ({ value: String(t.id), label: t.name }))}
              placeholder="Seleccionar tipo…"
            />
            {typesError && <span className="sud-hint">No se pudieron cargar los tipos.</span>}
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="cat-name">Nombre</label>
            <input
              id="cat-name"
              className="sud-input"
              type="text"
              placeholder="Nombre de la categoría"
              value={name}
              onChange={e => setName(e.target.value)}
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
