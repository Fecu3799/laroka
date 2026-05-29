import { useState, useEffect } from 'react'

export function useToast() {
  const [toasts, setToasts] = useState([])

  useEffect(() => {
    const handler = (e) => {
      const { message } = e.detail
      const id = Date.now() + Math.random()
      setToasts(prev => [...prev, { id, message }])
      setTimeout(() => {
        setToasts(prev => prev.filter(t => t.id !== id))
      }, 3500)
    }
    window.addEventListener('laroka:toast', handler)
    return () => window.removeEventListener('laroka:toast', handler)
  }, [])

  return toasts
}
