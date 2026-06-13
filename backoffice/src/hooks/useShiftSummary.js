import { useState, useEffect, useCallback, useRef } from 'react'
import useAuth from './useAuth'
import useBranch from './useBranch'
import { useShift } from '../context/ShiftContext'
import {
  getCurrentShift,
  getCurrentShiftSummary,
  getShiftHistory,
} from '../services/shiftsService'

const REFRESH_MS = 60_000

// Tres estados posibles:
//   'active' → turno abierto, summary calculado en vivo (refresca cada 60s)
//   'closed' → sin turno activo, muestra el último turno cerrado
//   'empty'  → no hay ningún turno registrado
//
// Nota: usamos el flag `active` de GET /shifts/current como discriminador en
// lugar del 404 de /current/summary, porque apiFetch dispara un toast de error
// ante cualquier 4xx — un 404 esperado ensuciaría la UX con un falso error.
export default function useShiftSummary() {
  const { token } = useAuth()
  const { activeBranchId } = useBranch()
  // Estado de turno compartido: cuando pasa de activo a null (cierre) o null a
  // activo (apertura), volvemos a evaluar qué mostrar.
  const { shift } = useShift()

  const [state, setState] = useState(null) // { mode, label, openedAt, openedBy, closedAt, summary }
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  // Mantiene el modo actual accesible dentro del intervalo sin recrearlo.
  const modeRef = useRef(null)

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
          summary,
        })
        modeRef.current = 'active'
        return
      }

      const history = await getShiftHistory(token, activeBranchId, 0, 1)
      const last = history?.content?.[0]
      if (!last) {
        setState({ mode: 'empty' })
        modeRef.current = 'empty'
        return
      }
      setState({
        mode: 'closed',
        label: 'ÚLTIMO TURNO CERRADO',
        openedAt: last.openedAt,
        openedBy: last.openedBy,
        closedAt: last.closedAt,
        summary: last.summary,
      })
      modeRef.current = 'closed'
    } catch {
      // apiFetch ya muestra el toast correspondiente.
      setError(true)
    } finally {
      if (!silent) setLoading(false)
    }
  }, [token, activeBranchId])

  // Carga inicial, al cambiar de sucursal y cada vez que el turno compartido se
  // abre o se cierra (transición null ↔ activo). Así, al cerrar el turno desde
  // esta pantalla, las métricas en vivo se reemplazan por el último turno cerrado
  // —o por el empty state si no hay historial.
  const activeShiftKey = shift?.openedAt ?? null
  useEffect(() => { load() }, [load, activeShiftKey])

  // Refresco automático cada 60s mientras haya un turno activo.
  useEffect(() => {
    const id = setInterval(() => {
      if (modeRef.current === 'active') load({ silent: true })
    }, REFRESH_MS)
    return () => clearInterval(id)
  }, [load])

  return { state, loading, error, refresh: load }
}
