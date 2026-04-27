import { useEffect, useRef, useState } from 'react'
import logoText from '../assets/logo-carga-1.png'
import logoPizza from '../assets/logo-carga-2.png'
import styles from './SplashScreen.module.css'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const MIN_DISPLAY_MS = 1200
const FADE_DURATION_MS = 300

export function SplashScreen({ onComplete }) {
  const [fading, setFading] = useState(false)
  const onCompleteRef = useRef(onComplete)
  onCompleteRef.current = onComplete

  useEffect(() => {
    let cancelled = false
    const startTime = Date.now()
    let timer1, timer2

    const finish = () => {
      if (cancelled) return
      setFading(true)
      timer2 = setTimeout(() => {
        if (!cancelled) onCompleteRef.current()
      }, FADE_DURATION_MS)
    }

    fetch(`${API_BASE}/branches`)
      .catch(() => {})
      .then(() => {
        if (cancelled) return
        const elapsed = Date.now() - startTime
        const remaining = Math.max(0, MIN_DISPLAY_MS - elapsed)
        timer1 = setTimeout(finish, remaining)
      })

    return () => {
      cancelled = true
      clearTimeout(timer1)
      clearTimeout(timer2)
    }
  }, [])

  return (
    <div
      className={`${styles.wrapper}${fading ? ` ${styles.fading}` : ''}`}
      role="status"
      aria-label="Cargando La Roka"
    >
      <div className={styles.logoComposite}>
        <img
          src={logoText}
          alt="La Roka Pizzería"
          className={styles.logoText}
          draggable={false}
        />
        <img
          src={logoPizza}
          alt=""
          aria-hidden="true"
          className={styles.logoPizza}
          draggable={false}
        />
      </div>
    </div>
  )
}
