import { useState } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { resetStaffUserPassword } from '../services/staffService'
import './ResetPasswordModal.css'

export default function ResetPasswordModal({ user, onClose, onDone }) {
  const { token } = useAuth()
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const tooShort = newPassword.length > 0 && newPassword.length < 8
  const mismatch = confirm.length > 0 && confirm !== newPassword
  const canSubmit = newPassword.length >= 8 && confirm === newPassword && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      await resetStaffUserPassword(user.id, newPassword, token)
      onDone()
      onClose()
    } catch (err) {
      setError(err?.message ?? 'No se pudo actualizar la contraseña.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const modal = (
    <div className="rpm-backdrop" onClick={handleClose}>
      <div
        className="rpm-dialog"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Resetear contraseña"
      >
        <div className="rpm-header">
          <h2 className="rpm-title">Resetear contraseña</h2>
          <button type="button" className="rpm-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <p className="rpm-sub">{user.name} · {user.email}</p>

        <form className="rpm-form" onSubmit={handleSubmit}>
          <div className="rpm-field">
            <label className="rpm-label" htmlFor="rpm-new">Nueva contraseña</label>
            <input
              id="rpm-new"
              className="rpm-input"
              type="password"
              placeholder="Mínimo 8 caracteres"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              autoComplete="new-password"
              autoFocus
            />
            {tooShort && <span className="rpm-hint">Debe tener al menos 8 caracteres.</span>}
          </div>

          <div className="rpm-field">
            <label className="rpm-label" htmlFor="rpm-confirm">Confirmar contraseña</label>
            <input
              id="rpm-confirm"
              className="rpm-input"
              type="password"
              placeholder="Repetir contraseña"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              autoComplete="new-password"
            />
            {mismatch && <span className="rpm-hint">Las contraseñas no coinciden.</span>}
          </div>

          {error && <p className="rpm-error">{error}</p>}

          <div className="rpm-actions">
            <button type="button" className="rpm-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="rpm-submit" disabled={!canSubmit}>
              {submitting ? 'Guardando…' : 'Actualizar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
