import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createStaffUser, updateStaffUser } from '../services/staffService'
import './StaffUserDrawer.css'

const ROLES = [
  { value: 'STAFF', label: 'Staff' },
  { value: 'MANAGER', label: 'Encargado' },
]

const EMPTY_FORM = { name: '', role: 'STAFF', branchId: '', password: '' }

export default function StaffUserDrawer({ open, mode, user, branches, onClose, onSaved }) {
  const { token } = useAuth()
  const isEdit = mode === 'edit'

  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  // Resultado de alta exitosa: muestra el email generado por el backend.
  const [created, setCreated] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    setCreated(null)
    if (isEdit && user) {
      setForm({
        name: user.name ?? '',
        role: user.role ?? 'STAFF',
        branchId: user.branchId != null ? String(user.branchId) : '',
        password: '',
      })
    } else {
      setForm(EMPTY_FORM)
    }
  }, [open, isEdit, user])

  function setField(key, value) {
    setForm(prev => ({ ...prev, [key]: value }))
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const nameValid = form.name.trim().length > 0
  const branchValid = form.branchId !== ''
  const passwordValid = isEdit || form.password.length >= 8
  const canSubmit = nameValid && branchValid && passwordValid && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      if (isEdit) {
        await updateStaffUser(user.id, {
          name: form.name.trim(),
          role: form.role,
          branchId: Number(form.branchId),
        }, token)
        onSaved()
        onClose()
      } else {
        const saved = await createStaffUser({
          name: form.name.trim(),
          password: form.password,
          role: form.role,
          branchId: Number(form.branchId),
        }, token)
        onSaved()
        // No cerramos: mostramos el email generado para entregar al empleado.
        setCreated(saved)
      }
    } catch (err) {
      // apiFetch ya emite un toast con el mensaje del backend (400/409). Lo
      // repetimos inline en el drawer para que quede visible junto al form.
      setError(err?.message ?? 'No se pudo guardar el empleado.')
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
        aria-label={isEdit ? 'Editar empleado' : 'Nuevo empleado'}
      >
        <div className="sud-header">
          <h2 className="sud-title">{isEdit ? 'Editar empleado' : 'Nuevo empleado'}</h2>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        {created ? (
          <div className="sud-success">
            <div className="sud-success-icon" aria-hidden="true">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
                <path d="m8 12 3 3 5-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            <p className="sud-success-title">Empleado creado</p>
            <p className="sud-success-sub">
              Entregá estas credenciales al empleado. El email fue generado automáticamente.
            </p>
            <div className="sud-readonly">
              <span className="sud-readonly-label">EMAIL</span>
              <span className="sud-readonly-value">{created.email}</span>
            </div>
            <button type="button" className="sud-submit" onClick={onClose}>Listo</button>
          </div>
        ) : (
          <form className="sud-form" onSubmit={handleSubmit}>
            <div className="sud-field">
              <label className="sud-label" htmlFor="sud-name">Nombre</label>
              <input
                id="sud-name"
                className="sud-input"
                type="text"
                placeholder="Nombre del empleado"
                value={form.name}
                onChange={e => setField('name', e.target.value)}
                autoFocus
              />
            </div>

            <div className="sud-field">
              <label className="sud-label" htmlFor="sud-role">Rol</label>
              <select
                id="sud-role"
                className="sud-input"
                value={form.role}
                onChange={e => setField('role', e.target.value)}
              >
                {ROLES.map(r => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div className="sud-field">
              <label className="sud-label" htmlFor="sud-branch">Sucursal</label>
              <select
                id="sud-branch"
                className="sud-input"
                value={form.branchId}
                onChange={e => setField('branchId', e.target.value)}
              >
                <option value="" disabled>Seleccionar sucursal…</option>
                {branches.map(b => (
                  <option key={b.id} value={String(b.id)}>{b.name}</option>
                ))}
              </select>
            </div>

            {!isEdit && (
              <div className="sud-field">
                <label className="sud-label" htmlFor="sud-password">Contraseña</label>
                <input
                  id="sud-password"
                  className="sud-input"
                  type="password"
                  placeholder="Mínimo 8 caracteres"
                  value={form.password}
                  onChange={e => setField('password', e.target.value)}
                  autoComplete="new-password"
                />
                {form.password.length > 0 && form.password.length < 8 && (
                  <span className="sud-hint">La contraseña debe tener al menos 8 caracteres.</span>
                )}
              </div>
            )}

            {isEdit && (
              <div className="sud-readonly">
                <span className="sud-readonly-label">EMAIL</span>
                <span className="sud-readonly-value">{user?.email}</span>
              </div>
            )}

            {error && <p className="sud-error">{error}</p>}

            <div className="sud-actions">
              <button type="button" className="sud-cancel" onClick={handleClose} disabled={submitting}>
                Cancelar
              </button>
              <button type="submit" className="sud-submit" disabled={!canSubmit}>
                {submitting ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear empleado'}
              </button>
            </div>
          </form>
        )}
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
