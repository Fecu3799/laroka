import { useState, useCallback, useEffect } from 'react'
import useAuth from './useAuth'
import useBranch from './useBranch'
import {
  getCurrentShift,
  openShift as apiOpenShift,
  closeShift as apiCloseShift,
} from '../services/shiftsService'

export default function useCurrentShift() {
  const { token } = useAuth()
  const { activeBranchId } = useBranch()

  const [shift, setShift] = useState(null)
  const [loading, setLoading] = useState(true)
  const [warning, setWarning] = useState(false)
  const [pendingShift, setPendingShift] = useState(null)
  const [closeSummary, setCloseSummary] = useState(null)

  const refresh = useCallback(async () => {
    if (!token || activeBranchId == null) { setLoading(false); return }
    setLoading(true)
    try {
      const data = await getCurrentShift(token, activeBranchId)
      setShift(data.active ? data : null)
    } catch { /* apiFetch already toasts */ } finally {
      setLoading(false)
    }
  }, [token, activeBranchId])

  useEffect(() => { refresh() }, [refresh])

  const openShift = useCallback(async () => {
    if (!token || activeBranchId == null) return
    setLoading(true)
    try {
      const data = await apiOpenShift(token, activeBranchId)
      const next = { active: true, shiftId: data.shiftId, openedAt: data.openedAt }
      if (data.warningPreviousShiftClosed) {
        setPendingShift(next)
        setWarning(true)
      } else {
        setShift(next)
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
  }, [pendingShift])

  const closeShift = useCallback(async () => {
    if (!token || activeBranchId == null) return
    setLoading(true)
    try {
      const summary = await apiCloseShift(token, activeBranchId)
      setShift(null)
      setCloseSummary(summary)
    } catch { /* noop */ } finally {
      setLoading(false)
    }
  }, [token, activeBranchId])

  const dismissSummary = useCallback(() => setCloseSummary(null), [])

  return { shift, loading, warning, confirmWarning, openShift, closeShift, closeSummary, dismissSummary }
}
