import styles from './OrderTrackingBanner.module.css'

function PhoneIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 11.5 19.79 19.79 0 01.01 2.88 2 2 0 012 .7h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L6.09 8.69a16 16 0 006.22 6.22l1.06-1.06a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function OrderTrackingBanner() {
  return (
    <div className={styles.banner}>
      {/* Fila superior: título + ícono teléfono */}
      <div className={styles.topRow}>
        <div className={styles.titleBlock}>
          <span className={styles.title}>Pedido en proceso</span>
          <span className={styles.eta}>Llega en ~30 min</span>
        </div>
        <button className={styles.phoneBtn} aria-label="Llamar al local">
          <PhoneIcon />
        </button>
      </div>

      {/* Badge de estado + dirección */}
      <div className={styles.metaRow}>
        <span className={styles.badge}>EN PREPARACIÓN</span>
        <span className={styles.address}>Av. Costanera 1240</span>
      </div>

      {/* Barra de progreso */}
      <div className={styles.progressRow}>
        <span className={styles.progressEmoji} aria-hidden="true">🏪</span>
        <div className={styles.progressTrack}>
          <div className={styles.progressFill} />
          <span
            className={styles.scooter}
            style={{ left: '30%' }}
            aria-hidden="true"
          >
            🛵
          </span>
        </div>
        <span className={styles.progressEmoji} aria-hidden="true">🏠</span>
      </div>
    </div>
  )
}
