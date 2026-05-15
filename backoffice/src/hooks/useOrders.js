import { useState, useCallback, useEffect } from 'react'

const API_URL = import.meta.env.VITE_API_URL ?? ''

export default function useOrders(token) {
  const [orders, setOrders]           = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)
  const [newOrderCount, setNewOrderCount] = useState(0)

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

  const refresh = useCallback(() => {
    setNewOrderCount(0)
    fetchOrders()
  }, [fetchOrders])

  const incrementNewOrders = useCallback(() => setNewOrderCount(n => n + 1), [])

  const dismissOrder = useCallback((id) => {
    setOrders(prev => prev.filter(o => o.id !== id))
  }, [])

  const updateOrderInList = useCallback((id, newStatus) => {
    setOrders(prev => prev.map(o => o.id === id ? { ...o, status: newStatus } : o))
  }, [])

  const updatePaymentInList = useCallback((id, paymentStatus) => {
    setOrders(prev => prev.map(o => o.id === id ? { ...o, paymentStatus } : o))
  }, [])

  return { orders, loading, error, newOrderCount, refresh, incrementNewOrders, dismissOrder, updateOrderInList, updatePaymentInList }
}
