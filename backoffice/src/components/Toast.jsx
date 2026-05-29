import { useToast } from '../hooks/useToast'
import './Toast.css'

export function Toast() {
  const toasts = useToast()
  if (toasts.length === 0) return null
  return (
    <div className="toast-container" aria-live="polite" aria-atomic="false">
      {toasts.map(t => (
        <div key={t.id} className="toast-item">{t.message}</div>
      ))}
    </div>
  )
}
