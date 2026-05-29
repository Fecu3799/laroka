import { useToast } from '../hooks/useToast'
import styles from './Toast.module.css'

export function Toast() {
  const toasts = useToast()
  if (toasts.length === 0) return null
  return (
    <div className={styles.container} aria-live="polite" aria-atomic="false">
      {toasts.map(t => (
        <div key={t.id} className={styles.toast}>{t.message}</div>
      ))}
    </div>
  )
}
