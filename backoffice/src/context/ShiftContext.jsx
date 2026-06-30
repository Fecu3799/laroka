import { createContext, useContext } from 'react'
import useCurrentShift from '../hooks/useCurrentShift'
import useShiftSummary from '../hooks/useShiftSummary'

const ShiftContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useShift() {
  const ctx = useContext(ShiftContext)
  if (!ctx) throw new Error('useShift debe usarse dentro de <ShiftProvider>')
  return ctx
}

// Única instancia de useCurrentShift y useShiftSummary para toda la sesión.
// SubHeader, OrdersProvider y la página de Resumen consumen el mismo estado de
// turno, por lo que cerrar el turno desde Resumen actualiza el sub-header
// automáticamente (estado compartido).
//
// El resumen también vive acá (no en la página): así sobrevive a la navegación
// entre pestañas y se muestra sin spinner al volver a Resumen. Se le pasa el
// openedAt del turno para que recargue al abrir/cerrar turno. Queda bajo la clave
// `summary` para no colisionar con `loading`/`error` de useCurrentShift.
export function ShiftProvider({ children }) {
  const shift = useCurrentShift()
  const summary = useShiftSummary(shift.shift?.openedAt ?? null)
  return (
    <ShiftContext.Provider value={{ ...shift, summary }}>
      {children}
    </ShiftContext.Provider>
  )
}
