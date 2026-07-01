import { useState } from 'react'
import { useConfig } from '../context/ConfigContext'
import BranchScheduleEditor from './BranchScheduleEditor'
import BranchDrawer from './BranchDrawer'

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
  // Sucursales desde el catálogo global cacheado (US-14-F-05).
  const { branches, loadingConfig, reloadBranches } = useConfig()

  const [expanded, setExpanded] = useState({})  // { [branchId]: bool } — horarios desplegados
  const [menuId, setMenuId] = useState(null)     // branchId con menú de acciones abierto
  const [drawer, setDrawer] = useState(null)     // { mode, branch } | null

  function openCreate() {
    setMenuId(null)
    setDrawer({ mode: 'create', branch: null })
  }

  function openEdit(branch) {
    setMenuId(null)
    setDrawer({ mode: 'edit', branch })
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
        <div className="config-branch-list">
          {branches.map(branch => {
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
                      onClick={() => setMenuId(menuId === branch.id ? null : branch.id)}
                      aria-label={`Acciones para ${branch.name}`}
                      aria-haspopup="true"
                      aria-expanded={menuId === branch.id}
                    >
                      <DotsIcon />
                    </button>
                    {menuId === branch.id && (
                      <div className="config-menu" role="menu">
                        <button className="config-menu-item" onClick={() => openEdit(branch)}>
                          Editar
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
    </section>
  )
}
