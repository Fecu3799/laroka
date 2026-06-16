import { useState, useEffect } from 'react'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { getShiftHistory } from '../services/shiftsService'
import { formatShiftDate, formatShiftClock, formatCurrency } from '../utils/shiftsUtils'
import ShiftDetailModal from './ShiftDetailModal'
import './History.css'

const PAGE_SIZE = 6

export default function History() {
  const { token } = useAuth()
  const { activeBranchId } = useBranch()
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    if (!token) return
    setLoading(true)
    setError(null)
    getShiftHistory(token, activeBranchId, page, PAGE_SIZE)
      .then(setData)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token, activeBranchId, page])

  const shifts = data?.content ?? []
  const totalPages = data?.page?.totalPages ?? 0
  const currentPage = data?.page?.number ?? 0

  return (
    <div className="history-page">
      <header className="history-header">
        <h1 className="history-title">Historial de turnos</h1>
        <p className="history-subtitle">Turnos cerrados con resumen de actividad</p>
      </header>

      {loading ? (
        <div className="history-state-center">
          <div className="history-spinner" />
        </div>
      ) : error ? (
        <div className="history-state-center">
          <p className="history-state-error">No se pudo cargar el historial.</p>
        </div>
      ) : shifts.length === 0 ? (
        <div className="history-state-center">
          <p className="history-empty">No hay turnos cerrados aún</p>
        </div>
      ) : (
        <>
          <div className="history-table-wrapper">
            <table className="history-table">
              <thead>
                <tr>
                  <th>FECHA</th>
                  <th>APERTURA</th>
                  <th>CIERRE</th>
                  <th>ENCARGADO</th>
                  <th>ENTREGADOS</th>
                  <th>INGRESOS TOTALES</th>
                </tr>
              </thead>
              <tbody>
                {shifts.map(shift => (
                  <tr
                    key={shift.shiftId}
                    className="history-row"
                    onClick={() => setSelected(shift)}
                  >
                    <td>{formatShiftDate(shift.openedAt)}</td>
                    <td className="history-mono">{formatShiftClock(shift.openedAt)}</td>
                    <td className="history-mono">{formatShiftClock(shift.closedAt)}</td>
                    <td>{shift.openedBy ?? '—'}</td>
                    <td className="history-delivered">{shift.summary?.deliveredOrders ?? 0}</td>
                    <td className="history-revenue">{formatCurrency(shift.summary?.totalRevenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="history-pagination">
              <button
                className="history-page-btn"
                onClick={() => setPage(p => p - 1)}
                disabled={currentPage === 0}
              >
                ← Anterior
              </button>
              <span className="history-page-info">
                Página {currentPage + 1} de {totalPages}
              </span>
              <button
                className="history-page-btn"
                onClick={() => setPage(p => p + 1)}
                disabled={currentPage >= totalPages - 1}
              >
                Siguiente →
              </button>
            </div>
          )}
        </>
      )}

      {selected && (
        <ShiftDetailModal shift={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  )
}
