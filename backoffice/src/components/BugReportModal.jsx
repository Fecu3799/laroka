import { useState } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { sendBugReport } from '../services/bugReportsService'
import './BugReportModal.css'

// US-17-F-03: modal simple para reportar bugs desde cualquier pantalla del
// backoffice. url y userAgent se capturan automáticamente al enviar — el operador
// solo escribe la descripción. El componente se monta al abrir y se desmonta al
// cerrar, así el textarea queda limpio tras un envío exitoso sin lógica extra.
export default function BugReportModal({ onClose }) {
  const { token } = useAuth()
  const { activeBranchId: branchId } = useBranch()
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const canSubmit = description.trim().length > 0 && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      await sendBugReport(
        {
          description: description.trim(),
          url: window.location.href,
          userAgent: navigator.userAgent,
        },
        token,
        branchId,
      )
      window.dispatchEvent(new CustomEvent('laroka:toast', { detail: { message: 'Reporte enviado' } }))
      onClose()
    } catch {
      setError('No se pudo enviar, intentá de nuevo.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const modal = (
    <div className="brm-backdrop" onClick={handleClose}>
      <div
        className="brm-dialog"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Reportar un problema"
      >
        <div className="brm-header">
          <h2 className="brm-title">Reportar un problema</h2>
          <button type="button" className="brm-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="brm-form" onSubmit={handleSubmit}>
          <div className="brm-field">
            <label className="brm-label" htmlFor="brm-desc">Contanos qué pasó</label>
            <textarea
              id="brm-desc"
              className="brm-textarea"
              placeholder="Contanos qué pasó"
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={5}
              autoFocus
            />
          </div>

          {error && <p className="brm-error">{error}</p>}

          <div className="brm-actions">
            <button type="button" className="brm-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="brm-submit" disabled={!canSubmit}>
              {submitting ? 'Enviando…' : 'Enviar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
