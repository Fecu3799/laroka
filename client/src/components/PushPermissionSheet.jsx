import { useEffect } from 'react'
import styles from './PushPermissionSheet.module.css'

function BellIcon() {
  return (
    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M13.7 21a2 2 0 01-3.4 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/**
 * Bottom sheet para el flujo de notificaciones push (US-09-F-01).
 * Dos variantes:
 *  - 'optin'   : pide activar notificaciones (Sí, activar / Ahora no)
 *  - 'install' : instrucciones ilustradas paso a paso para instalar la PWA en
 *                iOS Safari: ícono compartir → "Agregar a pantalla de inicio" →
 *                "Abrir desde el ícono". No pide permiso.
 *
 * Consistente con el sistema de diseño (mismas variables de paleta y tipografías
 * que PaymentModals).
 */
export function PushPermissionSheet({ open, variant, onAccept, onDismiss }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e) => { if (e.key === 'Escape') onDismiss() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onDismiss])

  if (!open) return null

  const isInstall = variant === 'install'

  return (
    <>
      <div className={styles.backdrop} onClick={onDismiss} aria-hidden="true" />
      <div
        className={styles.sheet}
        role="dialog"
        aria-modal="true"
        aria-labelledby="push-sheet-title"
      >
        <div className={styles.handle} aria-hidden="true" />
        <div className={styles.iconWrapper}>
          <BellIcon />
        </div>

        {isInstall ? (
          <>
            <h2 id="push-sheet-title" className={styles.title}>Activá las notificaciones</h2>
            <p className={styles.message}>
              Para recibir notificaciones, instalá la app desde Safari usando el ícono compartir ↑
            </p>
            <ol className={styles.steps}>
              <li>Tocá el ícono compartir <span aria-hidden="true">↑</span></li>
              <li>Elegí “Agregar a pantalla de inicio”</li>
              <li>Abrí la app desde el ícono</li>
            </ol>
            <button className={styles.btnPrimary} onClick={onDismiss}>
              ENTENDIDO
            </button>
          </>
        ) : (
          <>
            <h2 id="push-sheet-title" className={styles.title}>
              ¿Querés recibir notificaciones cuando tu pedido esté listo?
            </h2>
            <p className={styles.message}>
              Te avisamos en este dispositivo cada vez que tu pedido cambie de estado.
            </p>
            <button className={styles.btnPrimary} onClick={onAccept}>
              SÍ, ACTIVAR
            </button>
            <button className={styles.btnSecondary} onClick={onDismiss}>
              Ahora no
            </button>
          </>
        )}
      </div>
    </>
  )
}
