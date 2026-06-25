import { useState, useEffect, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import { fetchStaffUsers, setStaffUserStatus } from '../services/staffService'
import { fetchBranches } from '../services/branchService'
import StaffUserDrawer from '../components/StaffUserDrawer'
import ResetPasswordModal from '../components/ResetPasswordModal'
import './Config.css'

const ROLE_LABELS = { ADMIN: 'Admin', MANAGER: 'Encargado', STAFF: 'Staff' }

function DotsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  )
}

export default function Config() {
  const { token, role, userId } = useAuth()

  const [staff, setStaff] = useState([])
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const [menuId, setMenuId] = useState(null)
  const [drawer, setDrawer] = useState(null)        // { mode, user } | null
  const [resetUser, setResetUser] = useState(null)  // staff user | null
  const [confirm, setConfirm] = useState(null)      // { user, nextActive } | null
  const [confirmBusy, setConfirmBusy] = useState(false)
  const [confirmError, setConfirmError] = useState(null)

  const loadStaff = useCallback(() => {
    if (!token) return
    setLoading(true)
    setError(false)
    fetchStaffUsers(token)
      .then(setStaff)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token])

  useEffect(() => {
    loadStaff()
  }, [loadStaff])

  useEffect(() => {
    if (!token) return
    fetchBranches(token).then(setBranches).catch(() => setBranches([]))
  }, [token])

  // ADMIN únicamente — guard sincrónico.
  if (role && role !== 'ADMIN') return <Navigate to="/orders" replace />

  function openCreate() {
    setMenuId(null)
    setDrawer({ mode: 'create', user: null })
  }

  function openEdit(user) {
    setMenuId(null)
    setDrawer({ mode: 'edit', user })
  }

  function openReset(user) {
    setMenuId(null)
    setResetUser(user)
  }

  function openConfirmStatus(user) {
    setMenuId(null)
    setConfirmError(null)
    setConfirm({ user, nextActive: !user.active })
  }

  async function handleConfirmStatus() {
    if (!confirm) return
    setConfirmBusy(true)
    setConfirmError(null)
    try {
      await setStaffUserStatus(confirm.user.id, confirm.nextActive, token)
      setConfirm(null)
      loadStaff()
    } catch (err) {
      setConfirmError(err?.message ?? 'No se pudo actualizar el estado.')
    } finally {
      setConfirmBusy(false)
    }
  }

  return (
    <div className="config-page">
      <header className="config-header">
        <h1 className="config-title">Configuración</h1>
        <p className="config-subtitle">Administración del equipo y la operación</p>
      </header>

      <section className="config-section">
        <div className="config-section-head">
          <div>
            <h2 className="config-section-title">Equipo</h2>
            <p className="config-section-sub">Usuarios con acceso al backoffice</p>
          </div>
          <button className="config-new-btn" onClick={openCreate}>+ Nuevo empleado</button>
        </div>

        {loading ? (
          <div className="config-state-center"><div className="config-spinner" /></div>
        ) : error ? (
          <div className="config-state-center">
            <p className="config-state-error">No se pudo cargar el equipo.</p>
          </div>
        ) : staff.length === 0 ? (
          <div className="config-state-center">
            <p className="config-empty">Aún no hay empleados cargados.</p>
          </div>
        ) : (
          <div className="config-table-wrapper">
            <table className="config-table">
              <thead>
                <tr>
                  <th>NOMBRE</th>
                  <th>EMAIL</th>
                  <th>ROL</th>
                  <th>SUCURSAL</th>
                  <th>ESTADO</th>
                  <th aria-label="Acciones"></th>
                </tr>
              </thead>
              <tbody>
                {staff.map(u => {
                  const isSelf = String(u.id) === String(userId)
                  return (
                    <tr key={u.id} className={`config-row${u.active ? '' : ' config-row--inactive'}`}>
                      <td className="config-name">{u.name}</td>
                      <td className="config-mono">{u.email}</td>
                      <td>{ROLE_LABELS[u.role] ?? u.role}</td>
                      <td>{u.branchName ?? '—'}</td>
                      <td>
                        <span className={`config-badge${u.active ? ' config-badge--active' : ' config-badge--inactive'}`}>
                          {u.active ? 'Activo' : 'Inactivo'}
                        </span>
                      </td>
                      <td className="config-actions-cell">
                        <button
                          className="config-dots-btn"
                          onClick={() => setMenuId(menuId === u.id ? null : u.id)}
                          aria-label={`Acciones para ${u.name}`}
                          aria-haspopup="true"
                          aria-expanded={menuId === u.id}
                        >
                          <DotsIcon />
                        </button>
                        {menuId === u.id && (
                          <div className="config-menu" role="menu">
                            <button className="config-menu-item" onClick={() => openEdit(u)}>
                              Editar
                            </button>
                            <button className="config-menu-item" onClick={() => openReset(u)}>
                              Resetear contraseña
                            </button>
                            <button
                              className={`config-menu-item${u.active ? ' config-menu-item--danger' : ''}`}
                              onClick={() => openConfirmStatus(u)}
                              disabled={isSelf && u.active}
                              title={isSelf && u.active ? 'No podés desactivarte a vos mismo' : undefined}
                            >
                              {u.active ? 'Desactivar' : 'Activar'}
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Cierra el menú de acciones al hacer click fuera. */}
      {menuId !== null && (
        <div className="config-menu-backdrop" onClick={() => setMenuId(null)} aria-hidden="true" />
      )}

      {drawer && (
        <StaffUserDrawer
          open
          mode={drawer.mode}
          user={drawer.user}
          branches={branches}
          onClose={() => setDrawer(null)}
          onSaved={loadStaff}
        />
      )}

      {resetUser && (
        <ResetPasswordModal
          user={resetUser}
          onClose={() => setResetUser(null)}
          onDone={() => {}}
        />
      )}

      {confirm && (
        <div className="config-overlay" role="dialog" aria-modal="true" aria-labelledby="config-confirm-title">
          <div className="config-modal">
            <p className="config-modal-title" id="config-confirm-title">
              {confirm.nextActive ? '¿Activar empleado?' : '¿Desactivar empleado?'}
            </p>
            <p className="config-modal-body">
              {confirm.nextActive
                ? `${confirm.user.name} volverá a tener acceso al backoffice.`
                : `${confirm.user.name} dejará de poder iniciar sesión.`}
            </p>
            {confirmError && <p className="config-modal-error">{confirmError}</p>}
            <div className="config-modal-actions">
              <button
                className="config-modal-btn config-modal-btn--secondary"
                onClick={() => setConfirm(null)}
                disabled={confirmBusy}
              >
                Cancelar
              </button>
              <button
                className={`config-modal-btn ${confirm.nextActive ? 'config-modal-btn--primary' : 'config-modal-btn--danger'}`}
                onClick={handleConfirmStatus}
                disabled={confirmBusy}
              >
                {confirmBusy ? 'Aplicando…' : confirm.nextActive ? 'Activar' : 'Desactivar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
