import { useState, useCallback, useEffect } from 'react'
import useAuth from './useAuth'
import useBranch from './useBranch'
import {
  getCurrentShift,
  openShift as apiOpenShift,
  closeShift as apiCloseShift,
  toggleAcceptingOrders as apiToggleAcceptingOrders,
} from '../services/shiftsService'
import { fetchBranch } from '../services/branchService'

export default function useCurrentShift() {
  const { token } = useAuth()
  const { activeBranchId } = useBranch()

  const [shift, setShift] = useState(null)
  const [loading, setLoading] = useState(true)
  const [warning, setWarning] = useState(false)
  const [pendingShift, setPendingShift] = useState(null)
  const [closeSummary, setCloseSummary] = useState(null)
  const [acceptingOrders, setAcceptingOrders] = useState(false)
  const [suggestOrders, setSuggestOrders] = useState(false)

  const refresh = useCallback(async () => {
    if (!token || activeBranchId == null) { setLoading(false); return }
    setLoading(true)
    try {
      const data = await getCurrentShift(token, activeBranchId)
      if (data.active) {
        setShift(data)
        // El estado del toggle vive en Branch; lo leemos del endpoint público.
        try {
          const branch = await fetchBranch(token, activeBranchId)
          setAcceptingOrders(!!branch.acceptingOrders)
        } catch { setAcceptingOrders(false) }
      } else {
        setShift(null)
        setAcceptingOrders(false)
      }
    } catch { /* apiFetch already toasts */ } finally {
      setLoading(false)
    }
  }, [token, activeBranchId])

  useEffect(() => { refresh() }, [refresh])

  const toggleOrders = useCallback(async () => {
    if (!token || activeBranchId == null) return
    try {
      const data = await apiToggleAcceptingOrders(token, activeBranchId)
      setAcceptingOrders(!!data.acceptingOrders)
    } catch { /* apiFetch already toasts (422 si no hay turno activo) */ }
  }, [token, activeBranchId])

  const openShift = useCallback(async () => {
    if (!token || activeBranchId == null) return
    setLoading(true)
    try {
      const data = await apiOpenShift(token, activeBranchId)
      const next = { active: true, shiftId: data.shiftId, openedAt: data.openedAt }
      // Abrir turno siempre deja la recepción deshabilitada en el backend.
      setAcceptingOrders(false)
      if (data.warningPreviousShiftClosed) {
        setPendingShift(next)
        setWarning(true)
      } else {
        setShift(next)
        setSuggestOrders(true)
      }
    } catch { /* noop */ } finally {
      setLoading(false)
    }
  }, [token, activeBranchId])

  const confirmWarning = useCallback(() => {
    if (pendingShift) {
      setShift(pendingShift)
      setPendingShift(null)
    }
    setWarning(false)
    // Tras reconocer el cierre del turno previo, sugerir habilitar pedidos.
    setSuggestOrders(true)
  }, [pendingShift])

  const confirmSuggestOrders = useCallback(async () => {
    setSuggestOrders(false)
    await toggleOrders()
  }, [toggleOrders])

  const dismissSuggestOrders = useCallback(() => setSuggestOrders(false), [])

  const closeShift = useCallback(async () => {
    if (!token || activeBranchId == null) return
    setLoading(true)
    try {
      const summary = await apiCloseShift(token, activeBranchId)
      setShift(null)
      setAcceptingOrders(false)
      setCloseSummary(summary)
    } catch { /* noop */ } finally {
      setLoading(false)
    }
  }, [token, activeBranchId])

  const dismissSummary = useCallback(() => setCloseSummary(null), [])

  return {
    shift, loading, warning, confirmWarning,
    openShift, closeShift, closeSummary, dismissSummary,
    acceptingOrders, toggleOrders,
    suggestOrders, confirmSuggestOrders, dismissSuggestOrders,
  }
}
