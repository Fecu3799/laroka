import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import useBranch from '../hooks/useBranch'
import { useShift } from './ShiftContext'

const HistoryContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useHistory() {
  const ctx = useContext(HistoryContext)
  if (!ctx) throw new Error('useHistory debe usarse dentro de <HistoryProvider>')
  return ctx
}

// Cache por página del historial de turnos (US-14-F-05). Provider propio y liviano,
// NO parte de ConfigProvider, a propósito:
//   - El historial es paginado y de crecimiento ilimitado (una entrada por turno
//     cerrado, para siempre). Meterlo en ConfigProvider —cuyos datos son acotados
//     y estables por sesión— mezclaría dos ciclos de vida distintos.
//   - Su invalidación se dispara por un evento de dominio puntual (cierre de turno),
//     no por mutaciones CRUD como el resto del cache de configuración.
// Mantenerlo separado evita ese acoplamiento.
//
// La key incluye branchId para aislar el historial entre sucursales (ADMIN cambia
// de sucursal): al volver a una sucursal ya vista, sus páginas siguen cacheadas.
export function HistoryProvider({ children }) {
  const { activeBranchId } = useBranch()
  const { shift } = useShift()

  // Map<`${branchId}:${page}`, data>. Estado (no ref) para que la página montada
  // re-renderice cuando se invalida o se cachea una página nueva.
  const [cache, setCache] = useState(() => new Map())

  const getPage = useCallback(
    (branchId, page) => cache.get(`${branchId}:${page}`) ?? null,
    [cache],
  )

  const putPage = useCallback((branchId, page, data) => {
    setCache(prev => new Map(prev).set(`${branchId}:${page}`, data))
  }, [])

  // Invalidación total al cerrar un turno (manual o auto): el cierre agrega un
  // turno nuevo a la primera página, dejando todo el cache stale.
  const prevShiftRef = useRef(shift)
  useEffect(() => {
    const prev = prevShiftRef.current
    prevShiftRef.current = shift
    // Transición turno activo → cerrado.
    if (prev && !shift) setCache(new Map())
  }, [shift])

  return (
    <HistoryContext.Provider value={{ getPage, putPage, activeBranchId }}>
      {children}
    </HistoryContext.Provider>
  )
}
