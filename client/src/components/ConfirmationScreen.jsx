import { useEffect, useRef } from 'react'
import repartidorImg from '../assets/repartidor.png'
import styles from './ConfirmationScreen.module.css'

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

    timerRef.current = setTimeout(onComplete, 3000)
    return () => clearTimeout(timerRef.current)
  }, [])

  return (
    <div className={styles.screen}>
      <div className={styles.content}>
        <img
          src={repartidorImg}
          alt=""
          aria-hidden="true"
          className={styles.illustration}
        />
        <h1 className={styles.title}>¡Pedido confirmado!</h1>
        <p className={styles.subtitle}>
          Pedido recibido! En breves estará en preparación
        </p>
      </div>
      <div className={styles.progressBar} />
    </div>
  )
}
