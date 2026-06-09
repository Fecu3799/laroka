import { useEffect, useRef } from 'react'
import repartidorImg from '../assets/repartidor.png'
import styles from './ConfirmationScreen.module.css'
import { addActiveOrder } from '../utils/activeOrders'

// 12 particles distributed along upper semicircular arc around the illustration
const PARTICLES = [
  { left: '10%', top: '38%', size: 5, delay: 0 },
  { left: '15%', top: '31%', size: 8, delay: 80 },
  { left: '20%', top: '25%', size: 6, delay: 160 },
  { left: '28%', top: '20%', size: 9, delay: 240 },
  { left: '36%', top: '17%', size: 5, delay: 40 },
  { left: '45%', top: '15%', size: 7, delay: 300 },
  { left: '55%', top: '15%', size: 6, delay: 420 },
  { left: '64%', top: '17%', size: 8, delay: 120 },
  { left: '72%', top: '20%', size: 5, delay: 200 },
  { left: '80%', top: '25%', size: 9, delay: 380 },
  { left: '85%', top: '31%', size: 6, delay: 500 },
  { left: '90%', top: '38%', size: 7, delay: 60 },
]

const TRAILS = [
  { opacity: 0.12 },
  { opacity: 0.07 },
  { opacity: 0.03 },
]

export function ConfirmationScreen({ orderId, branchId, onComplete }) {
  const timerRef = useRef(null)

  useEffect(() => {
    addActiveOrder(orderId, branchId)
    timerRef.current = setTimeout(onComplete, 3000)
    return () => clearTimeout(timerRef.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className={styles.screen}>
      <div className={styles.content}>
        <div className={styles.illustrationWrapper}>
          {PARTICLES.map((p, i) => (
            <div
              key={i}
              className={styles.particle}
              style={{
                left: p.left,
                top: p.top,
                width: p.size,
                height: p.size,
                animationDelay: `${p.delay}ms`,
              }}
            />
          ))}

          {TRAILS.map((t, i) => (
            <img
              key={i}
              src={repartidorImg}
              alt=""
              aria-hidden="true"
              className={styles.trailImage}
              style={{ opacity: t.opacity }}
            />
          ))}

          <img
            src={repartidorImg}
            alt=""
            aria-hidden="true"
            className={styles.illustration}
          />
        </div>

        <h1 className={styles.title}>¡Pedido confirmado!</h1>

        <div className={styles.titleLine} />

        <p className={styles.subtitle}>Pedido recibido! En breves estará en preparación</p>
      </div>

      <div className={styles.progressTrack}>
        <div className={styles.progressFill} />
      </div>
    </div>
  )
}
