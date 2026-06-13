import { useState, useCallback, useEffect } from 'react'
const API_URL = import.meta.env.VITE_API_URL ?? ''

export default function useOrders(token, branchId = null, shiftId = null, shiftReady = true) {
  const [orders, setOrders]           = useState([])
  const [loading, setLoading]         = useState(true)
  const [error, setError]             = useState(null)
  const [dismissedIds, setDismissedIds] = useState(() => {
    try {
      const stored = localStorage.getItem('laroka_dismissed_ids')
      return stored ? new Set(JSON.parse(stored)) : new Set()
    } catch { return new Set() }
  })

  const fetchOrders = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const headers = { Authorization: `Bearer ${token}` }
      if (branchId != null) headers['X-Branch-Id'] = String(branchId)
      const url = new URL(`${API_URL}/backoffice/orders`)
      if (shiftId) url.searchParams.set('shiftId', shiftId)
      const res = await fetch(url.toString(), { headers })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setOrders(await res.json())
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [token, branchId, shiftId])

  // No disparamos hasta que el estado del turno haya resuelto en useCurrentShift,
  // así evitamos el doble fetch null → shiftId (loading se mantiene en true mientras tanto).
  useEffect(() => { if (shiftReady) fetchOrders() }, [fetchOrders, shiftReady])

  useEffect(() => {
    try {
      localStorage.setItem('laroka_dismissed_ids', JSON.stringify([...dismissedIds]))
    } catch { /* noop */ }
  }, [dismissedIds])

  const refresh = useCallback(() => {
    fetchOrders()
  }, [fetchOrders])

  const clearOrders = useCallback(() => {
    setOrders([])
  }, [])

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

  return { orders, loading, error, refresh, clearOrders, dismissOrder, dismissedIds, updateOrderInList, updatePaymentInList, replaceOrderInList, updateSingleOrder: replaceOrderInList }
}
