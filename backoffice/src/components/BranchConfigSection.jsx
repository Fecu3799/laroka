import { useState } from 'react'
import useAuth from '../hooks/useAuth'
import { useConfig } from '../context/ConfigContext'
import { setBranchStatus } from '../services/branchService'
import BranchScheduleEditor from './BranchScheduleEditor'
import BranchDrawer from './BranchDrawer'
import BranchProductsDrawer from './BranchProductsDrawer'

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function DotsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  )
}

export default function BranchConfigSection() {
  const { token } = useAuth()
  // Sucursales desde el catálogo global cacheado (US-14-F-05). Incluye activas e
  // inactivas: GET /backoffice/branches devuelve todas con su flag `active`.
  const { branches, loadingConfig, reloadBranches } = useConfig()

  const [expanded, setExpanded] = useState({})  // { [branchId]: bool } — horarios desplegados
  const [menuId, setMenuId] = useState(null)     // branchId con menú de acciones abierto
  const [menuUp, setMenuUp] = useState(false)    // abre hacia arriba si no hay espacio abajo
  const [drawer, setDrawer] = useState(null)     // { mode, branch } | null
  const [productsDrawer, setProductsDrawer] = useState(null) // branch | null — gestión de productos
  const [showArchived, setShowArchived] = useState(false)
  const [confirm, setConfirm] = useState(null)   // { branch, nextActive } | null
  const [confirmBusy, setConfirmBusy] = useState(false)
  const [confirmError, setConfirmError] = useState(null)

  const activeBranches = branches.filter(b => b.active)
  const archivedBranches = branches.filter(b => !b.active)

  // Abre/cierra el menú de acciones decidiendo la dirección según el espacio
  // disponible bajo el trigger respecto al viewport (evita que el dropdown del
  // último ítem se corte al final de la pantalla).
  function toggleMenu(e, branchId) {
    if (menuId === branchId) {
      setMenuId(null)
      return
    }
    const rect = e.currentTarget.getBoundingClientRect()
    const MENU_HEIGHT = 150 // alto aproximado del dropdown (hasta 3 items + padding)
    const spaceBelow = window.innerHeight - rect.bottom
    setMenuUp(spaceBelow < MENU_HEIGHT)
    setMenuId(branchId)
  }

  function openCreate() {
    setMenuId(null)
    setDrawer({ mode: 'create', branch: null })
  }

  function openEdit(branch) {
    setMenuId(null)
    setDrawer({ mode: 'edit', branch })
  }

  function openProductsManager(branch) {
    setMenuId(null)
    setProductsDrawer(branch)
  }

  function openConfirmStatus(branch) {
    setMenuId(null)
    setConfirmError(null)
    setConfirm({ branch, nextActive: !branch.active })
  }

  async function handleConfirmStatus() {
    if (!confirm) return
    setConfirmBusy(true)
    setConfirmError(null)
    try {
      await setBranchStatus(confirm.branch.id, confirm.nextActive, token)
      setConfirm(null)
      reloadBranches()
    } catch (err) {
      // Muestra el mensaje del backend tal cual (ej. 400 por turno abierto).
      setConfirmError(err?.message ?? 'No se pudo actualizar el estado.')
    } finally {
      setConfirmBusy(false)
    }
  }

  return (
    <section className="config-section">
      <div className="config-section-head">
        <div>
          <h2 className="config-section-title">Sucursales</h2>
          <p className="config-section-sub">Datos y horarios de cada sucursal</p>
        </div>
        <button className="config-new-btn" onClick={openCreate}>+ Nueva sucursal</button>
      </div>

      {loadingConfig ? (
        <div className="config-state-center"><div className="config-spinner" /></div>
      ) : branches.length === 0 ? (
        <div className="config-state-center">
          <p className="config-empty">No hay sucursales para configurar.</p>
        </div>
      ) : (
        <>
          {activeBranches.length === 0 ? (
            <div className="config-state-center">
              <p className="config-empty">No hay sucursales activas.</p>
            </div>
          ) : (
            <div className="config-branch-list">
              {activeBranches.map(branch => {
                const isOpen = !!expanded[branch.id]
                return (
                  <div key={branch.id} className="config-branch-item">
                    <div className="config-branch-row">
                      <div className="config-branch-info">
                        <span className="config-branch-name">{branch.name}</span>
                        <span className="config-branch-label">{branch.address}</span>
                      </div>

                      <div className="config-branch-actions">
                        <button
                          className="config-dots-btn"
                          onClick={(e) => toggleMenu(e, branch.id)}
                          aria-label={`Acciones para ${branch.name}`}
                          aria-haspopup="true"
                          aria-expanded={menuId === branch.id}
                        >
                          <DotsIcon />
                        </button>
                        {menuId === branch.id && (
                          <div className={`config-menu${menuUp ? ' config-menu--up' : ''}`} role="menu">
                            <button className="config-menu-item" onClick={() => openEdit(branch)}>
                              Editar
                            </button>
                            <button className="config-menu-item" onClick={() => openProductsManager(branch)}>
                              Gestionar productos
                            </button>
                            <button className="config-menu-item config-menu-item--danger" onClick={() => openConfirmStatus(branch)}>
                              Desactivar
                            </button>
                          </div>
                        )}
                      </div>
                    </div>

                    <button
                      className={`config-schedule-toggle${isOpen ? ' config-schedule-toggle--open' : ''}`}
                      onClick={() => setExpanded(p => ({ ...p, [branch.id]: !p[branch.id] }))}
                      aria-expanded={isOpen}
                    >
                      <span className="config-schedule-chevron"><ChevronIcon /></span>
                      Horarios
                    </button>

                    {isOpen && <BranchScheduleEditor branchId={branch.id} />}
                  </div>
                )
              })}
            </div>
          )}

          {archivedBranches.length > 0 && (
            <div className="config-archived">
              <button
                className={`config-archived-toggle${showArchived ? ' config-archived-toggle--open' : ''}`}
                onClick={() => setShowArchived(s => !s)}
                aria-expanded={showArchived}
              >
                <span className="config-archived-chevron"><ChevronIcon /></span>
                Archivadas ({archivedBranches.length})
              </button>
              {showArchived && (
                <div className="config-branch-list">
                  {archivedBranches.map(branch => (
                    <div key={branch.id} className="config-branch-item">
                      <div className="config-branch-row">
                        <div className="config-branch-info">
                          <span className="config-branch-name">{branch.name}</span>
                          <span className="config-branch-label">{branch.address}</span>
                        </div>

                        <div className="config-branch-actions">
                          <button
                            className="config-dots-btn"
                            onClick={(e) => toggleMenu(e, branch.id)}
                            aria-label={`Acciones para ${branch.name}`}
                            aria-haspopup="true"
                            aria-expanded={menuId === branch.id}
                          >
                            <DotsIcon />
                          </button>
                          {menuId === branch.id && (
                            <div className={`config-menu${menuUp ? ' config-menu--up' : ''}`} role="menu">
                              {/* Archivada: fuera de operación → solo se puede reactivar. */}
                              <button className="config-menu-item" onClick={() => openConfirmStatus(branch)}>
                                Activar
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Cierra el menú de acciones al hacer click fuera. */}
      {menuId !== null && (
        <div className="config-menu-backdrop" onClick={() => setMenuId(null)} aria-hidden="true" />
      )}

      {drawer && (
        <BranchDrawer
          open
          mode={drawer.mode}
          branch={drawer.branch}
          onClose={() => setDrawer(null)}
          onSaved={reloadBranches}
        />
      )}

      {productsDrawer && (
        <BranchProductsDrawer
          branch={productsDrawer}
          onClose={() => setProductsDrawer(null)}
        />
      )}

      {confirm && (
        <div className="config-overlay" role="dialog" aria-modal="true" aria-labelledby="branch-confirm-title">
          <div className="config-modal">
            <p className="config-modal-title" id="branch-confirm-title">
              {confirm.nextActive ? '¿Activar sucursal?' : '¿Desactivar sucursal?'}
            </p>
            <p className="config-modal-body">
              {confirm.nextActive
                ? `«${confirm.branch.name}» volverá a estar disponible para los clientes.`
                : `«${confirm.branch.name}» dejará de mostrarse a los clientes y quedará archivada.`}
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
    </section>
  )
}
