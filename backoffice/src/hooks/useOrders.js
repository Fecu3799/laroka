import { useState, useCallback, useEffect } from 'react'
const API_URL = import.meta.env.VITE_API_URL ?? ''

export default function useOrders(token) {
  const [orders, setOrders]           = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)
  const [dismissedIds, setDismissedIds] = useState(() => {
    try {
      const stored = sessionStorage.getItem('laroka_dismissed_ids')
      return stored ? new Set(JSON.parse(stored)) : new Set()
    } catch { return new Set() }
  })

  const fetchOrders = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${API_URL}/backoffice/orders`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setOrders(await res.json())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [token])

  useEffect(() => { fetchOrders() }, [fetchOrders])

  useEffect(() => {
    try {
      sessionStorage.setItem('laroka_dismissed_ids', JSON.stringify([...dismissedIds]))
    } catch { /* noop */ }
  }, [dismissedIds])

  const refresh = useCallback(() => {
    fetchOrders()
  }, [fetchOrders])

  const dismissOrder = useCallback((id) => {
    setDismissedIds(prev => new Set([...prev, id]))
  }, [])

  const updateOrderInList = useCallback((id, newStatus) => {
    setOrders(prev => prev.map(o => o.id === id ? { ...o, status: newStatus } : o))
  }, [])

  const updatePaymentInList = useCallback((id, paymentStatus) => {
    setOrders(prev => prev.map(o => o.id === id ? { ...o, paymentStatus } : o))
  }, [])

  const replaceOrderInList = useCallback((order) => {
    setOrders(prev => {
      const idx = prev.findIndex(o => o.id === order.id)
      if (idx === -1) return [order, ...prev]
      return prev.map(o => o.id === order.id ? order : o)
    })
  }, [])

  return { orders, loading, error, refresh, dismissOrder, dismissedIds, updateOrderInList, updatePaymentInList, replaceOrderInList }
}
