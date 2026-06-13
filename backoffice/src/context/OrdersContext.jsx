import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { useShift } from './ShiftContext'
import useOrders from '../hooks/useOrders'
import useOrderDetail from '../hooks/useOrderDetail'

const OrdersContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useOrdersContext() {
  const ctx = useContext(OrdersContext)
  if (!ctx) throw new Error('useOrdersContext debe usarse dentro de <OrdersProvider>')
  return ctx
}

// Vive en Layout y persiste durante toda la sesión, por lo que la lista, el
// turno y el feed sobreviven a la navegación entre vistas (Orders se desmonta
// al ir a /shifts/summary o /history pero el estado no se pierde ni se vuelve a fetchear).
export function OrdersProvider({ setOpenOrderId, children }) {
  const { token } = useAuth()
  const { activeBranchId: branchId } = useBranch()
  const { shift, ready } = useShift()

  const {
    orders,
    loading,
    error,
    refresh,
    clearOrders,
    dismissOrder,
    dismissedIds,
    updateOrderInList,
    updatePaymentInList,
    replaceOrderInList,
    updateSingleOrder,
  } = useOrders(token, branchId, shift?.shiftId ?? null, ready)

  const [selectedId, setSelectedId] = useState(null)
  const { detail, refetchDetail } = useOrderDetail(selectedId, token, branchId)

  // ── Flash + pending dot state ────────────────────────────────
  const [flashedIds, setFlashedIds] = useState(new Set())
  const [pendingOrderIds, setPendingOrderIds] = useState(new Map())
  const flashTimeoutsRef = useRef(new Map())

  const flashOrder = useCallback((orderId) => {
    setFlashedIds(prev => new Set([...prev, orderId]))
    if (flashTimeoutsRef.current.has(orderId)) clearTimeout(flashTimeoutsRef.current.get(orderId))
    const tid = setTimeout(() => {
      setFlashedIds(prev => { const s = new Set(prev); s.delete(orderId); return s })
      flashTimeoutsRef.current.delete(orderId)
    }, 600)
    flashTimeoutsRef.current.set(orderId, tid)
  }, [])

  useEffect(() => {
    const map = flashTimeoutsRef.current
    return () => map.forEach(clearTimeout)
  }, [])

  // ── Refresh when a new order is created via the modal ────────
  useEffect(() => {
    function handleOrderCreated() {
      refresh()
    }
    window.addEventListener('laroka:order-created', handleOrderCreated)
    return () => window.removeEventListener('laroka:order-created', handleOrderCreated)
  }, [refresh])

  // ── Feed item pressed in SubHeader → merge + flash ───────────
  useEffect(() => {
    function handleOrderInsert(e) {
      if (e.detail?.order) {
        const { order } = e.detail
        updateSingleOrder(order)
        flashOrder(order.id)
      }
    }
    window.addEventListener('laroka:order-insert', handleOrderInsert)
    return () => window.removeEventListener('laroka:order-insert', handleOrderInsert)
  }, [updateSingleOrder, flashOrder])

  // ── Sync pending dot set from SubHeader feed ─────────────────
  useEffect(() => {
    function handle(e) { setPendingOrderIds(e.detail?.orderColorMap ?? new Map()) }
    window.addEventListener('laroka:feed-updated', handle)
    return () => window.removeEventListener('laroka:feed-updated', handle)
  }, [])

  // ── Remove pending dot when detail panel opens ───────────────
  useEffect(() => {
    if (!selectedId) return
    setPendingOrderIds(prev => {
      if (!prev.has(selectedId)) return prev
      const m = new Map(prev); m.delete(selectedId); return m
    })
  }, [selectedId])

  // ── Sync open panel ID to Layout ref ─────────────────────────
  useEffect(() => { setOpenOrderId(selectedId) }, [selectedId, setOpenOrderId])

  // ── SSE: actualiza lista en tiempo real; refresca detalle si el panel está abierto ─
  // Vive en el provider para seguir actualizando filas aunque Orders esté desmontado.
  useEffect(() => {
    function handleOrderUpdated(e) {
      const { orderId, type, order } = e.detail
      if (type === 'ORDER_UPDATED' && order) {
        replaceOrderInList(order)
        if (selectedId === orderId) refetchDetail()
      } else if (type === 'CANCELLATION_REQUESTED') {
        if (selectedId && orderId === selectedId) {
          updateOrderInList(orderId, 'CANCELLATION_REQUESTED')
          refetchDetail()
        }
      }
    }
    window.addEventListener('laroka:order-updated', handleOrderUpdated)
    return () => window.removeEventListener('laroka:order-updated', handleOrderUpdated)
  }, [selectedId, refetchDetail, updateOrderInList, replaceOrderInList])

  // ── Clear list and close panel synchronously on branch change ─
  useEffect(() => {
    clearOrders()
    setSelectedId(null)
  }, [branchId, clearOrders])

  // ── Clear list when a new shift is opened ────────────────────
  // El turno se abre desde el sub-header (no desde aquí); detectamos la
  // transición sin-turno → turno-activo en el estado compartido y vaciamos la
  // lista local antes de que el nuevo fetch (disparado por shiftId) la repueble.
  const prevShiftKeyRef = useRef(null)
  const currentShiftKey = shift?.shiftId ?? null
  useEffect(() => {
    if (currentShiftKey && prevShiftKeyRef.current === null) {
      clearOrders()
    }
    prevShiftKeyRef.current = currentShiftKey
  }, [currentShiftKey, clearOrders])

  // ── Auto-clear selected if order leaves visible list ─────────
  useEffect(() => {
    if (selectedId && !orders.find((o) => o.id === selectedId)) {
      setSelectedId(null)
    }
  }, [selectedId, orders])

  const value = {
    orders,
    loading,
    error,
    shift,
    ready,
    selectedId,
    setSelectedId,
    refresh,
    clearOrders,
    dismissOrder,
    dismissedIds,
    updateOrderInList,
    updatePaymentInList,
    replaceOrderInList,
    updateSingleOrder,
    pendingOrderIds,
    flashedIds,
    flashOrder,
    detail,
    refetchDetail,
  }

  return <OrdersContext.Provider value={value}>{children}</OrdersContext.Provider>
}
