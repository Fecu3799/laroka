import { createContext, useContext } from 'react'
import useCurrentShift from '../hooks/useCurrentShift'

const ShiftContext = createContext(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useShift() {
  const ctx = useContext(ShiftContext)
  if (!ctx) throw new Error('useShift debe usarse dentro de <ShiftProvider>')
  return ctx
}

// Única instancia de useCurrentShift para toda la sesión. SubHeader, OrdersProvider
// y la página de Resumen consumen el mismo estado de turno, por lo que cerrar el
// turno desde Resumen actualiza el sub-header automáticamente (estado compartido).
export function ShiftProvider({ children }) {
  const shift = useCurrentShift()
  return <ShiftContext.Provider value={shift}>{children}</ShiftContext.Provider>
}
