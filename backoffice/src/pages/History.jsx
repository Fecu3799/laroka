import { useState, useEffect } from 'react'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { getShiftHistory } from '../services/shiftsService'
import { downloadShiftSummary } from '../services/ticketService'
import { useHistory } from '../context/HistoryContext'
import { formatShiftDate, formatShiftClock, formatCurrency } from '../utils/shiftsUtils'
import ShiftDetailModal from './ShiftDetailModal'
import './History.css'

const PAGE_SIZE = 6

function DownloadIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <polyline points="7 10 12 15 17 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12 15V3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export default function History() {
  const { token, tenantName } = useAuth()
  const { activeBranchName } = useBranch()
  // Cache por página en HistoryProvider: sobrevive al desmontaje de la pestaña,
  // así una página ya visitada se muestra al instante sin spinner ni refetch.
  const { getPage, putPage, activeBranchId } = useHistory()
  const [page, setPage] = useState(0)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)

  // Derivado del cache: si la página está cacheada, no hay spinner ni fetch.
  const data = getPage(activeBranchId, page)

  useEffect(() => {
    if (!token || data) return
    let cancelled = false
    setError(null)
    getShiftHistory(token, activeBranchId, page, PAGE_SIZE)
      .then(d => { if (!cancelled) putPage(activeBranchId, page, d) })
      .catch(() => { if (!cancelled) setError(true) })
    return () => { cancelled = true }
  }, [token, activeBranchId, page, data, putPage])

  // Descarga el resumen (Informe Z) de un turno cerrado usando los datos ya
  // cacheados por el provider — sin fetch nuevo. La fila del historial ya es un
  // ShiftHistoryItemDTO { shiftId, openedAt, closedAt, summary }, la forma que
  // ShiftSummaryDocument consume.
  async function handleDownload(e, shift) {
    e.stopPropagation()
    try {
      await downloadShiftSummary(shift, { name: activeBranchName, tenantName })
    } catch {
      /* silent */
    }
  }

  // Sin datos y sin error ⇒ cargando (incluye el primer render antes del fetch).
  const loading = !data && !error
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
                  <th className="history-th-action" aria-label="Acciones"></th>
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
                    <td className="history-action-cell">
                      <button
                        className="history-download-btn"
                        type="button"
                        onClick={(e) => handleDownload(e, shift)}
                        aria-label="Descargar resumen del turno"
                        title="Descargar resumen (PDF)"
                      >
                        <DownloadIcon />
                      </button>
                    </td>
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
