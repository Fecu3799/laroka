import { useCallback, useRef, useState } from 'react'
import { upsertPushSubscription } from '../services/pushService'

const OPTIN_KEY = 'laroka_push_optin_shown'
const INSTALL_KEY = 'laroka_push_install_shown'

/**
 * Encapsula la lógica de suscripción Web Push para clientes anónimos
 * (US-09-F-01 / US-09-F-02). La suscripción es por dispositivo/browser.
 *
 * Devuelve, además de las funciones de suscripción, el estado del bottom sheet
 * (`sheet`) y los handlers de sus botones, para que el componente que confirma
 * el pedido lo renderice. Las funciones que muestran el sheet (`requestPermission
 * AndSubscribe`, `showInstallInstructions`) se auto-limitan a una vez por sesión
 * vía sessionStorage.
 */
export function usePushSubscription() {
  const [sheet, setSheet] = useState({ open: false, variant: null })
  const resolverRef = useRef(null)

  const settle = useCallback((value) => {
    setSheet({ open: false, variant: null })
    const resolve = resolverRef.current
    resolverRef.current = null
    if (resolve) resolve(value)
  }, [])

  // Abre el bottom sheet y resuelve cuando el usuario interactúa con un botón.
  const openSheet = useCallback((variant) => {
    return new Promise((resolve) => {
      resolverRef.current = resolve
      setSheet({ open: true, variant })
    })
  }, [])

  const acceptSheet = useCallback(() => settle(true), [settle])
  const dismissSheet = useCallback(() => settle(false), [settle])

  /**
   * Obtiene la suscripción existente o la crea (si el permiso está concedido),
   * la registra en el backend y devuelve su id. Nunca lanza: ante cualquier
   * fallo retorna null.
   */
  const getOrCreateSubscription = useCallback(async () => {
    try {
      if (!('serviceWorker' in navigator) || !('PushManager' in window)) return null

      const registration = await navigator.serviceWorker.ready
      let subscription = await registration.pushManager.getSubscription()

      if (!subscription) {
        if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return null
        const vapidPublicKey = import.meta.env.VITE_VAPID_PUBLIC_KEY
        if (!vapidPublicKey) return null
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: vapidPublicKey,
        })
      }

      return await upsertPushSubscription(subscription)
    } catch {
      return null
    }
  }, [])

  /**
   * Muestra el bottom sheet de opt-in; si el usuario acepta, pide permiso y
   * suscribe. Una vez por sesión. Retorna el subscriptionId o null.
   */
  const requestPermissionAndSubscribe = useCallback(async () => {
    if (sessionStorage.getItem(OPTIN_KEY)) return null
    sessionStorage.setItem(OPTIN_KEY, '1')

    const accepted = await openSheet('optin')
    if (!accepted) return null

    try {
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') return null
    } catch {
      return null
    }

    return getOrCreateSubscription()
  }, [openSheet, getOrCreateSubscription])

  /**
   * Muestra el bottom sheet con instrucciones de instalación (iOS Safari no
   * instalado). No pide permiso. Una vez por sesión. Siempre retorna null.
   */
  const showInstallInstructions = useCallback(async () => {
    if (sessionStorage.getItem(INSTALL_KEY)) return null
    sessionStorage.setItem(INSTALL_KEY, '1')
    await openSheet('install')
    return null
  }, [openSheet])

  return {
    sheet,
    acceptSheet,
    dismissSheet,
    getOrCreateSubscription,
    requestPermissionAndSubscribe,
    showInstallInstructions,
  }
}
