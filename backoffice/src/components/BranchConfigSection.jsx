import { useState, useEffect, useCallback, useRef } from 'react'
import useAuth from '../hooks/useAuth'
import { fetchBackofficeBranches, updateBranchConfig } from '../services/branchService'

// minutos → horas como string editable ('' si no hay valor cargado).
function minutesToHours(minutes) {
  if (minutes == null) return ''
  const h = minutes / 60
  return String(h)
}

export default function BranchConfigSection() {
  const { token, tenantId } = useAuth()

  const [rows, setRows] = useState([])      // { id, name, hours, original, status, error }
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const timersRef = useRef({})

  const load = useCallback(() => {
    if (!token) return
    setLoading(true)
    setError(false)
    fetchBackofficeBranches(token, tenantId)
      .then(list => {
        setRows(list.map(b => {
          const hours = minutesToHours(b.maxShiftDurationMinutes)
          return { id: b.id, name: b.name, hours, original: hours, status: 'idle', error: null }
        }))
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token, tenantId])

  useEffect(() => { load() }, [load])

  // Limpia timeouts de feedback al desmontar.
  useEffect(() => {
    const timers = timersRef.current
    return () => Object.values(timers).forEach(clearTimeout)
  }, [])

  function patchRow(id, patch) {
    setRows(prev => prev.map(r => (r.id === id ? { ...r, ...patch } : r)))
  }

  function handleChange(id, value) {
    // Solo dígitos y un punto decimal.
    if (value !== '' && !/^\d*\.?\d*$/.test(value)) return
    patchRow(id, { hours: value, status: 'idle', error: null })
  }

  async function handleSave(row) {
    const hoursNum = parseFloat(row.hours)
    if (!Number.isFinite(hoursNum) || hoursNum < 1) {
      patchRow(row.id, { status: 'error', error: 'Mínimo 1 hora.' })
      return
    }
    const minutes = Math.round(hoursNum * 60)
    if (timersRef.current[row.id]) clearTimeout(timersRef.current[row.id])
    patchRow(row.id, { status: 'saving', error: null })
    try {
      const updated = await updateBranchConfig(row.id, minutes, token)
      const hours = minutesToHours(updated.maxShiftDurationMinutes)
      patchRow(row.id, { hours, original: hours, status: 'saved', error: null })
      timersRef.current[row.id] = setTimeout(() => patchRow(row.id, { status: 'idle' }), 2500)
    } catch (err) {
      patchRow(row.id, { status: 'error', error: err?.message ?? 'No se pudo guardar.' })
    }
  }

  return (
    <section className="config-section">
      <div className="config-section-head">
        <div>
          <h2 className="config-section-title">Sucursales</h2>
          <p className="config-section-sub">Duración máxima de turno por sucursal</p>
        </div>
      </div>

      {loading ? (
        <div className="config-state-center"><div className="config-spinner" /></div>
      ) : error ? (
        <div className="config-state-center">
          <p className="config-state-error">No se pudieron cargar las sucursales.</p>
        </div>
      ) : rows.length === 0 ? (
        <div className="config-state-center">
          <p className="config-empty">No hay sucursales para configurar.</p>
        </div>
      ) : (
        <div className="config-branch-list">
          {rows.map(row => {
            const invalid = !(parseFloat(row.hours) >= 1)
            const dirty = row.hours !== row.original
            const saving = row.status === 'saving'
            return (
              <div key={row.id} className="config-branch-row">
                <div className="config-branch-info">
                  <span className="config-branch-name">{row.name}</span>
                  <span className="config-branch-label">Duración máxima de turno</span>
                </div>

                <div className="config-branch-control">
                  <div className="config-hours-field">
                    <input
                      className="config-hours-input"
                      type="number"
                      min="1"
                      step="0.5"
                      inputMode="decimal"
                      value={row.hours}
                      placeholder="—"
                      onChange={e => handleChange(row.id, e.target.value)}
                      aria-label={`Duración máxima de turno para ${row.name} en horas`}
                    />
                    <span className="config-hours-unit">horas</span>
                  </div>

                  <button
                    className="config-branch-save"
                    onClick={() => handleSave(row)}
                    disabled={invalid || saving || !dirty}
                  >
                    {saving ? 'Guardando…' : 'Guardar'}
                  </button>

                  <span className="config-branch-feedback">
                    {invalid && dirty
                      ? <span className="config-feedback-err">La duración máxima es obligatoria</span>
                      : row.status === 'saved'
                        ? <span className="config-feedback-ok">✓ Guardado</span>
                        : row.status === 'error'
                          ? <span className="config-feedback-err">{row.error}</span>
                          : null
                    }
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
