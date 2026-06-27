import styles from './ConfigError.module.css'

// Se muestra cuando VITE_TENANT_ID no está configurado. La app no puede
// resolver a qué tenant pertenecen las sucursales, por lo que no se realiza
// ninguna llamada al backend hasta que la variable esté presente. El detalle
// técnico se loguea por consola; al usuario sólo se le muestra un mensaje
// genérico.
export function ConfigError() {
  console.error('[ConfigError] Falta la variable de entorno VITE_TENANT_ID')
  return (
    <div className={styles.wrapper} role="alert">
      <div className={styles.card}>
        <div className={styles.icon} aria-hidden="true">⚠️</div>
        <h1 className={styles.title}>Servicio no disponible</h1>
        <p className={styles.message}>
          Esta aplicación no está disponible en este momento. Por favor intentá
          más tarde o contactá al administrador.
        </p>
      </div>
    </div>
  )
}
