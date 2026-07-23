import { useState, useEffect, useCallback } from 'react'
import useAuth from './useAuth'
import useBranch from './useBranch'
import {
  getCurrentShift,
  getCurrentShiftSummary,
  getShiftHistory,
} from '../services/shiftsService'

// Tres estados posibles:
//   'active' → turno abierto, summary calculado en vivo
//   'closed' → sin turno activo, muestra el último turno cerrado
//   'empty'  → no hay ningún turno registrado
//
// Nota: usamos el flag `active` de GET /shifts/current como discriminador en
// lugar del 404 de /current/summary, porque apiFetch dispara un toast de error
// ante cualquier 4xx — un 404 esperado ensuciaría la UX con un falso error.
//
// Este hook se instancia una única vez en ShiftProvider (no en la página), por
// lo que el estado del resumen sobrevive a la navegación entre pestañas. No hace
// polling: el resumen solo recarga (1) al montar o al cambiar de turno —cambia
// `activeShiftKey`—, (2) al recibir un evento SSE ORDER_UPDATED con status
// DELIVERED o CANCELLED, que son los únicos que modifican las métricas.
//
// `activeShiftKey` es el openedAt del turno compartido (o null). Se recibe por
// parámetro —en vez de leer useShift() acá— porque el provider que expone el
// turno es el mismo que instancia este hook.
export default function useShiftSummary(activeShiftKey) {
  const { token } = useAuth()
  const { activeBranchId } = useBranch()

  const [state, setState] = useState(null) // { mode, label, openedAt, openedBy, closedAt, summary }
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const load = useCallback(async ({ silent = false } = {}) => {
    if (!token || activeBranchId == null) {
      setLoading(false)
      return
    }
    if (!silent) setLoading(true)
    setError(false)
    try {
      const current = await getCurrentShift(token, activeBranchId)

      if (current?.active) {
        const summary = await getCurrentShiftSummary(token, activeBranchId)
        setState({
          mode: 'active',
          label: 'TURNO ACTUAL',
          openedAt: current.openedAt,
          openedBy: current.openedBy,
          closedAt: null,
          // Un turno en curso nunca está auto-cerrado (Informe X).
          autoClose: false,
          summary,
        })
        return
      }

      const history = await getShiftHistory(token, activeBranchId, 0, 1)
      const last = history?.content?.[0]
      if (!last) {
        setState({ mode: 'empty' })
        return
      }
      setState({
        mode: 'closed',
        label: 'ÚLTIMO TURNO CERRADO',
        openedAt: last.openedAt,
        openedBy: last.openedBy,
        closedAt: last.closedAt,
        // closedBy viaja en el ShiftHistoryItemDTO (nombre, o null si auto-cerró).
        // Lo consume ShiftSummaryDocument para el "Cerrado por" del PDF (US-20-01).
        closedBy: last.closedBy,
        // Expuesto por el backend (US-16-04): true si el turno se auto-cerró.
        // Lo consume ShiftSummaryDocument vía shift.autoClose.
        autoClose: last.autoClose ?? false,
        summary: last.summary,
      })
    } catch {
      // apiFetch ya muestra el toast correspondiente.
      setError(true)
    } finally {
      if (!silent) setLoading(false)
    }
  }, [token, activeBranchId])

  // Carga inicial, al cambiar de sucursal y cada vez que el turno compartido se
  // abre o se cierra (transición null ↔ activo). Así, al cerrar el turno, las
  // métricas en vivo se reemplazan por el último turno cerrado —o por el empty
  // state si no hay historial.
  useEffect(() => { load() }, [load, activeShiftKey])

  // Recarga silenciosa cuando un pedido se entrega o cancela. El evento lo emite
  // Layout.jsx al procesar el SSE ORDER_UPDATED con status DELIVERED/CANCELLED.
  // Silenciosa: la pantalla muestra los datos previos sin spinner mientras tanto.
  useEffect(() => {
    function handle() { load({ silent: true }) }
    window.addEventListener('laroka:shift-summary-stale', handle)
    return () => window.removeEventListener('laroka:shift-summary-stale', handle)
  }, [load])

  return { state, loading, error, refresh: load }
}
