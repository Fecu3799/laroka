import { useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import repartidorImg from '../assets/repartidor.png'
import styles from './ConfirmationScreen.module.css'

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

// Trail copies lag behind main image — positive delay = lags in animation time
const TRAILS = [
  { opacity: 0.12, delay: 0.07 },
  { opacity: 0.07, delay: 0.14 },
  { opacity: 0.03, delay: 0.21 },
]

// Spring handles both the fast easeOut-like entry and the oscillation bounce
const SPRING = { type: 'spring', stiffness: 180, damping: 12 }

export function ConfirmationScreen({ orderId, onComplete }) {
  const timerRef = useRef(null)

  useEffect(() => {
    try {
      const raw = localStorage.getItem('laroka_active_orders')
      const orders = raw ? JSON.parse(raw) : []
      if (!orders.includes(orderId)) {
        orders.push(orderId)
        localStorage.setItem('laroka_active_orders', JSON.stringify(orders))
      }
    } catch {
      localStorage.setItem('laroka_active_orders', JSON.stringify([orderId]))
    }

    // timer disabled — screen stays until further notice
    timerRef.current = setTimeout(onComplete, 3000)
    return () => clearTimeout(timerRef.current)
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
            <motion.img
              key={i}
              src={repartidorImg}
              alt=""
              aria-hidden="true"
              className={styles.trailImage}
              style={{ opacity: t.opacity }}
              initial={{ x: 400 }}
              animate={{ x: 0 }}
              transition={{ ...SPRING, delay: t.delay }}
            />
          ))}

          <motion.img
            src={repartidorImg}
            alt=""
            aria-hidden="true"
            className={styles.illustration}
            initial={{ x: 400 }}
            animate={{ x: 0 }}
            transition={SPRING}
          />
        </div>

        <motion.h1
          className={styles.title}
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.45 }}
        >
          ¡Pedido confirmado!
        </motion.h1>

        <div className={styles.titleLine} />

        <motion.p
          className={styles.subtitle}
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.6 }}
        >
          Pedido recibido! En breves estará en preparación
        </motion.p>
      </div>

      <div className={styles.progressTrack}>
        <div className={styles.progressFill} />
      </div>
    </div>
  )
}
