import { useState, useEffect, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import { fetchCategories, deleteCategory } from '../services/catalogService'
import CategoryDrawer from '../components/CategoryDrawer'
import './Config.css'

function DotsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  )
}

export default function Menu() {
  const { token, role, tenantId } = useAuth()
  const isAdmin = role === 'ADMIN'

  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const [menuId, setMenuId] = useState(null)
  const [drawer, setDrawer] = useState(null)        // { mode, category } | null
  const [confirm, setConfirm] = useState(null)      // category | null
  const [confirmBusy, setConfirmBusy] = useState(false)
  const [confirmError, setConfirmError] = useState(null)

  const loadCategories = useCallback(() => {
    if (!token) return
    setLoading(true)
    setError(false)
    fetchCategories(token, tenantId)
      .then(setCategories)
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [token, tenantId])

  useEffect(() => {
    loadCategories()
  }, [loadCategories])

  // ADMIN y MANAGER únicamente — STAFF no ve la pestaña. Guard sincrónico.
  if (role && role !== 'ADMIN' && role !== 'MANAGER') return <Navigate to="/orders" replace />

  function openCreate() {
    setMenuId(null)
    setDrawer({ mode: 'create', category: null })
  }

  function openEdit(category) {
    setMenuId(null)
    setDrawer({ mode: 'edit', category })
  }

  function openDelete(category) {
    setMenuId(null)
    setConfirmError(null)
    setConfirm(category)
  }

  async function handleDelete() {
    if (!confirm) return
    setConfirmBusy(true)
    setConfirmError(null)
    try {
      await deleteCategory(confirm.id, token)
      setConfirm(null)
      loadCategories()
    } catch (err) {
      setConfirmError(err?.message ?? 'No se pudo eliminar la categoría.')
    } finally {
      setConfirmBusy(false)
    }
  }

  return (
    <div className="config-page">
      <header className="config-header">
        <h1 className="config-title">Menú</h1>
        <p className="config-subtitle">Gestión de categorías y productos</p>
      </header>

      <div className="config-layout">
        <div className="config-col config-col--main">
          <section className="config-section">
            <div className="config-section-head">
              <div>
                <h2 className="config-section-title">Categorías</h2>
                <p className="config-section-sub">Organizá los productos del menú</p>
              </div>
              {isAdmin && (
                <button className="config-new-btn" onClick={openCreate}>+ Nueva categoría</button>
              )}
            </div>

            {loading ? (
              <div className="config-state-center"><div className="config-spinner" /></div>
            ) : error ? (
              <div className="config-state-center">
                <p className="config-state-error">No se pudieron cargar las categorías.</p>
              </div>
            ) : categories.length === 0 ? (
              <div className="config-state-center">
                <p className="config-empty">Aún no hay categorías cargadas.</p>
              </div>
            ) : (
              <div className="config-table-wrapper">
                <table className="config-table">
                  <thead>
                    <tr>
                      <th>NOMBRE</th>
                      <th>PRODUCTOS</th>
                      {isAdmin && <th aria-label="Acciones"></th>}
                    </tr>
                  </thead>
                  <tbody>
                    {categories.map(c => (
                      <tr key={c.id} className="config-row">
                        <td className="config-name">{c.name}</td>
                        <td>{c.productCount ?? 0}</td>
                        {isAdmin && (
                          <td className="config-actions-cell">
                            <button
                              className="config-dots-btn"
                              onClick={() => setMenuId(menuId === c.id ? null : c.id)}
                              aria-label={`Acciones para ${c.name}`}
                              aria-haspopup="true"
                              aria-expanded={menuId === c.id}
                            >
                              <DotsIcon />
                            </button>
                            {menuId === c.id && (
                              <div className="config-menu" role="menu">
                                <button className="config-menu-item" onClick={() => openEdit(c)}>
                                  Editar
                                </button>
                                <button className="config-menu-item config-menu-item--danger" onClick={() => openDelete(c)}>
                                  Eliminar
                                </button>
                              </div>
                            )}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      </div>

      {/* Cierra el menú de acciones al hacer click fuera. */}
      {menuId !== null && (
        <div className="config-menu-backdrop" onClick={() => setMenuId(null)} aria-hidden="true" />
      )}

      {drawer && (
        <CategoryDrawer
          open
          mode={drawer.mode}
          category={drawer.category}
          onClose={() => setDrawer(null)}
          onSaved={loadCategories}
        />
      )}

      {confirm && (
        <div className="config-overlay" role="dialog" aria-modal="true" aria-labelledby="menu-confirm-title">
          <div className="config-modal">
            <p className="config-modal-title" id="menu-confirm-title">¿Eliminar categoría?</p>
            <p className="config-modal-body">
              La categoría «{confirm.name}» será eliminada.
              {confirm.productCount > 0 && (
                <> Esta categoría tiene {confirm.productCount} {confirm.productCount === 1 ? 'producto asociado' : 'productos asociados'}.</>
              )}
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
                className="config-modal-btn config-modal-btn--danger"
                onClick={handleDelete}
                disabled={confirmBusy}
              >
                {confirmBusy ? 'Eliminando…' : 'Eliminar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
