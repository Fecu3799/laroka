import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createStaffUser, updateStaffUser } from '../services/staffService'
import CustomSelect from './CustomSelect'
import './StaffUserDrawer.css'

const ROLES = [
  { value: 'STAFF', label: 'Staff' },
  { value: 'MANAGER', label: 'Encargado' },
]

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M10.6 6.1A9.6 9.6 0 0 1 12 6c6.5 0 10 7 10 7a16.2 16.2 0 0 1-2.3 3.2M6.6 6.6A16 16 0 0 0 2 13s3.5 7 10 7a9.5 9.5 0 0 0 4.4-1.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3 3l18 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

const EMPTY_FORM = { name: '', role: 'STAFF', branchId: '', password: '' }

export default function StaffUserDrawer({ open, mode, user, branches, onClose, onSaved }) {
  const { token } = useAuth()
  const isEdit = mode === 'edit'

  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const [showPassword, setShowPassword] = useState(false)
  // Resultado de alta exitosa: muestra el email generado por el backend.
  const [created, setCreated] = useState(null)

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    setCreated(null)
    setShowPassword(false)
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
              <span className="sud-label" id="sud-role-label">Rol</span>
              <CustomSelect
                id="sud-role"
                ariaLabelledBy="sud-role-label"
                value={form.role}
                onChange={val => setField('role', val)}
                options={ROLES}
              />
            </div>

            <div className="sud-field">
              <span className="sud-label" id="sud-branch-label">Sucursal</span>
              <CustomSelect
                id="sud-branch"
                ariaLabelledBy="sud-branch-label"
                value={form.branchId}
                onChange={val => setField('branchId', val)}
                options={branches.map(b => ({ value: String(b.id), label: b.name }))}
                placeholder="Seleccionar sucursal…"
              />
            </div>

            {!isEdit && (
              <div className="sud-field">
                <label className="sud-label" htmlFor="sud-password">Contraseña</label>
                <div className="sud-input-wrap">
                  <input
                    id="sud-password"
                    className="sud-input sud-input--with-icon"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Mínimo 8 caracteres"
                    value={form.password}
                    onChange={e => setField('password', e.target.value)}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    className="sud-eye"
                    onClick={() => setShowPassword(s => !s)}
                    aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    aria-pressed={showPassword}
                  >
                    {showPassword ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
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
