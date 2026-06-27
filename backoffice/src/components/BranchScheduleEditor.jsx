import { useState, useEffect, useCallback, useRef } from 'react'
import useAuth from '../hooks/useAuth'
import ToggleSwitch from './ToggleSwitch'
import { fetchBranchSchedule, saveBranchSchedule } from '../services/branchService'

const DAYS = [
  { key: 'MON', label: 'Lunes' },
  { key: 'TUE', label: 'Martes' },
  { key: 'WED', label: 'Miércoles' },
  { key: 'THU', label: 'Jueves' },
  { key: 'FRI', label: 'Viernes' },
  { key: 'SAT', label: 'Sábado' },
  { key: 'SUN', label: 'Domingo' },
]

// '' si no hay valor; las horas vienen del backend como 'HH:mm'.
function timeValue(t) {
  return t ?? ''
}

// Construye el estado del formulario (7 días en orden fijo) desde la respuesta.
function toRows(response) {
  const byDay = {}
  ;(response ?? []).forEach(d => { byDay[d.dayOfWeek] = d })
  return DAYS.map(({ key }) => {
    const d = byDay[key] ?? {}
    return {
      dayOfWeek: key,
      active: !!d.active,
      openTime: timeValue(d.openTime),
      closeTime: timeValue(d.closeTime),
      openTime2: timeValue(d.openTime2),
      closeTime2: timeValue(d.closeTime2),
      hasSecond: !!(d.openTime2 || d.closeTime2),
    }
  })
}

// Un día es válido si: inactivo (sin requisitos), o activo con franja 1 completa
// y —si tiene segunda franja— franja 2 completa.
function dayValid(d) {
  if (!d.active) return true
  if (!d.openTime || !d.closeTime) return false
  if (d.hasSecond && (!d.openTime2 || !d.closeTime2)) return false
  return true
}

export default function BranchScheduleEditor({ branchId }) {
  const { token } = useAuth()

  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [status, setStatus] = useState('idle')   // idle | saving | saved | error
  const [saveError, setSaveError] = useState(null)
  const timerRef = useRef(null)

  const load = useCallback(() => {
    if (!token) return
    setLoading(true)
    setError(false)
    fetchBranchSchedule(branchId, token)
      .then(data => setRows(toRows(data)))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token, branchId])

  useEffect(() => { load() }, [load])

  useEffect(() => () => clearTimeout(timerRef.current), [])

  function patchDay(key, patch) {
    setRows(prev => prev.map(d => (d.dayOfWeek === key ? { ...d, ...patch } : d)))
    setStatus('idle')
    setSaveError(null)
  }

  const canSave = rows.length > 0 && rows.every(dayValid)

  async function handleSave() {
    if (!canSave) return
    clearTimeout(timerRef.current)
    setStatus('saving')
    setSaveError(null)
    try {
      const payload = rows.map(d => ({
        dayOfWeek: d.dayOfWeek,
        active: d.active,
        openTime: d.active ? d.openTime : null,
        closeTime: d.active ? d.closeTime : null,
        openTime2: d.active && d.hasSecond ? d.openTime2 : null,
        closeTime2: d.active && d.hasSecond ? d.closeTime2 : null,
      }))
      const saved = await saveBranchSchedule(branchId, payload, token)
      setRows(toRows(saved))
      setStatus('saved')
      timerRef.current = setTimeout(() => setStatus('idle'), 2500)
    } catch (err) {
      setStatus('error')
      setSaveError(err?.message ?? 'No se pudo guardar.')
    }
  }

  if (loading) {
    return <div className="config-state-center"><div className="config-spinner" /></div>
  }

  if (error) {
    return (
      <div className="config-state-center">
        <p className="config-state-error">No se pudieron cargar los horarios.</p>
      </div>
    )
  }

  const saving = status === 'saving'

  return (
    <div className="config-schedule">
      <div className="config-schedule-days">
        {rows.map(d => {
          const label = DAYS.find(x => x.key === d.dayOfWeek)?.label ?? d.dayOfWeek
          return (
            <div
              key={d.dayOfWeek}
              className={`config-schedule-day${d.active ? '' : ' config-schedule-day--off'}`}
            >
              <div className="config-schedule-day-head">
                <span className="config-schedule-day-name">{label}</span>
                <ToggleSwitch
                  checked={d.active}
                  onChange={e => patchDay(d.dayOfWeek, { active: e.target.checked })}
                  label={d.active ? 'Abierto' : 'Cerrado'}
                />
              </div>

              <div className="config-schedule-slots">
                <div className="config-schedule-slot">
                  <input
                    type="time"
                    className="config-schedule-time"
                    value={d.openTime}
                    disabled={!d.active}
                    onChange={e => patchDay(d.dayOfWeek, { openTime: e.target.value })}
                    aria-label={`${label}: hora de apertura`}
                  />
                  <span className="config-schedule-sep">a</span>
                  <input
                    type="time"
                    className="config-schedule-time"
                    value={d.closeTime}
                    disabled={!d.active}
                    onChange={e => patchDay(d.dayOfWeek, { closeTime: e.target.value })}
                    aria-label={`${label}: hora de cierre`}
                  />
                </div>

                <label className="config-schedule-second-toggle">
                  <input
                    type="checkbox"
                    checked={d.hasSecond}
                    disabled={!d.active}
                    onChange={e => patchDay(d.dayOfWeek, { hasSecond: e.target.checked })}
                  />
                  Segunda franja
                </label>

                {d.active && d.hasSecond && (
                  <div className="config-schedule-slot">
                    <input
                      type="time"
                      className="config-schedule-time"
                      value={d.openTime2}
                      onChange={e => patchDay(d.dayOfWeek, { openTime2: e.target.value })}
                      aria-label={`${label}: hora de apertura (franja 2)`}
                    />
                    <span className="config-schedule-sep">a</span>
                    <input
                      type="time"
                      className="config-schedule-time"
                      value={d.closeTime2}
                      onChange={e => patchDay(d.dayOfWeek, { closeTime2: e.target.value })}
                      aria-label={`${label}: hora de cierre (franja 2)`}
                    />
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>

      <div className="config-profile-actions">
        <button
          className="config-branch-save"
          onClick={handleSave}
          disabled={!canSave || saving}
        >
          {saving ? 'Guardando…' : 'Guardar horarios'}
        </button>
        <span className="config-branch-feedback">
          {!canSave
            ? <span className="config-feedback-err">Completá las horas de los días abiertos</span>
            : status === 'saved'
              ? <span className="config-feedback-ok">✓ Guardado</span>
              : status === 'error'
                ? <span className="config-feedback-err">{saveError}</span>
                : null}
        </span>
      </div>
    </div>
  )
}
