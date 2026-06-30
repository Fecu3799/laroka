import { useState, useEffect, useCallback } from 'react'
import { Navigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import {
  deleteCategory,
  deleteProduct,
  fetchBranchMenu,
  updateProductAvailability,
} from '../services/catalogService'
import { useConfig } from '../context/ConfigContext'
import { formatCurrency } from '../utils/shiftsUtils'
import CategoryDrawer from '../components/CategoryDrawer'
import ProductDrawer from '../components/ProductDrawer'
import ToggleSwitch from '../components/ToggleSwitch'
import './Config.css'
import './Menu.css'

function DotsIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  )
}

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function ImagePlaceholderIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="8.5" cy="9.5" r="1.6" stroke="currentColor" strokeWidth="1.4" />
      <path d="m4 17 4.5-4.5a2 2 0 0 1 2.8 0L20 19" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export default function Menu() {
  const { token, role, branchId } = useAuth()
  const isAdmin = role === 'ADMIN'
  const isManager = role === 'MANAGER'

  // Catálogo global cacheado (US-14-F-05): categorías y productos vienen del
  // ConfigProvider; las mutaciones invalidan vía reloadCategories/reloadProducts.
  const { categories, products, loadingConfig, reloadCategories, reloadProducts } = useConfig()

  const [menuId, setMenuId] = useState(null)
  const [drawer, setDrawer] = useState(null)        // { mode, category } | null
  const [confirm, setConfirm] = useState(null)      // category | null
  const [confirmBusy, setConfirmBusy] = useState(false)
  const [confirmError, setConfirmError] = useState(null)
  const [confirmStep, setConfirmStep] = useState(1) // 2 pasos si la categoría tiene productos

  // ── Productos (US-14-F-02) ──────────────────────────────────────
  const [collapsed, setCollapsed] = useState(() => new Set()) // categoryIds colapsadas
  const [productMenuId, setProductMenuId] = useState(null)
  const [productMenuUp, setProductMenuUp] = useState(false)   // abre hacia arriba si no hay espacio abajo
  const [productDrawer, setProductDrawer] = useState(null)         // { mode, product } | null
  const [productConfirm, setProductConfirm] = useState(null)       // product | null
  const [productConfirmBusy, setProductConfirmBusy] = useState(false)
  const [productConfirmError, setProductConfirmError] = useState(null)

  // ── Disponibilidad inline por sucursal (US-14-F-04, solo MANAGER) ───
  // El menú de sucursal devuelve solo los productos disponibles: ese set
  // permite derivar la disponibilidad de cada producto para la sucursal del token.
  const [availableMenuIds, setAvailableMenuIds] = useState(null)   // Set | null
  const [availability, setAvailability] = useState({})            // { [productId]: boolean }

  // Disponibilidad por sucursal: solo MANAGER. branchId se resuelve del token.
  // Sigue siendo local: es específica de la sucursal del MANAGER, no es global.
  const loadAvailability = useCallback(() => {
    if (role !== 'MANAGER' || !token || branchId == null) return
    fetchBranchMenu(branchId, token)
      .then(menu => {
        const ids = new Set(menu.flatMap(c => (c.products ?? []).map(p => p.id)))
        setAvailableMenuIds(ids)
      })
      .catch(() => setAvailableMenuIds(new Set()))
  }, [role, token, branchId])

  useEffect(() => {
    loadAvailability()
  }, [loadAvailability])

  // Deriva el mapa de disponibilidad cuando hay productos y el menú de sucursal cargado.
  useEffect(() => {
    if (!isManager || availableMenuIds == null) return
    const map = {}
    for (const p of products) map[p.id] = availableMenuIds.has(p.id)
    setAvailability(map)
  }, [isManager, products, availableMenuIds])

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
    setConfirmStep(1)
    setConfirm(category)
  }

  // Primer paso: si la categoría tiene productos, pasa al segundo paso con la advertencia;
  // si no, elimina directamente.
  function handleConfirmFirstStep() {
    if (confirm?.productCount > 0) {
      setConfirmError(null)
      setConfirmStep(2)
    } else {
      handleDelete()
    }
  }

  async function handleDelete() {
    if (!confirm) return
    setConfirmBusy(true)
    setConfirmError(null)
    try {
      await deleteCategory(confirm.id, token)
      setConfirm(null)
      reloadCategories()
    } catch (err) {
      setConfirmError(err?.message ?? 'No se pudo eliminar la categoría.')
    } finally {
      setConfirmBusy(false)
    }
  }

  function toggleCollapse(categoryId) {
    setCollapsed(prev => {
      const next = new Set(prev)
      if (next.has(categoryId)) next.delete(categoryId)
      else next.add(categoryId)
      return next
    })
  }

  // Abre/cierra el menú de acciones de un producto, decidiendo la dirección
  // según el espacio disponible debajo del trigger respecto al viewport.
  function toggleProductMenu(e, productId) {
    if (productMenuId === productId) {
      setProductMenuId(null)
      return
    }
    const rect = e.currentTarget.getBoundingClientRect()
    const MENU_HEIGHT = 110 // alto aproximado del dropdown (2 items + padding)
    const spaceBelow = window.innerHeight - rect.bottom
    setProductMenuUp(spaceBelow < MENU_HEIGHT)
    setProductMenuId(productId)
  }

  // Optimistic update: cambia el toggle de inmediato y revierte si el backend falla.
  async function toggleAvailability(product) {
    const current = !!availability[product.id]
    const next = !current
    setAvailability(prev => ({ ...prev, [product.id]: next }))
    try {
      await updateProductAvailability(product.id, next, token)
    } catch {
      setAvailability(prev => ({ ...prev, [product.id]: current }))
    }
  }

  function openProductCreate() {
    setProductMenuId(null)
    setProductDrawer({ mode: 'create', product: null })
  }

  function openProductEdit(product) {
    setProductMenuId(null)
    setProductDrawer({ mode: 'edit', product })
  }

  function openProductDelete(product) {
    setProductMenuId(null)
    setProductConfirmError(null)
    setProductConfirm(product)
  }

  async function handleProductDelete() {
    if (!productConfirm) return
    setProductConfirmBusy(true)
    setProductConfirmError(null)
    try {
      await deleteProduct(productConfirm.id, token)
      setProductConfirm(null)
      reloadProducts()
      // El borrado afecta el productCount de la categoría.
      reloadCategories()
    } catch (err) {
      setProductConfirmError(err?.message ?? 'No se pudo eliminar el producto.')
    } finally {
      setProductConfirmBusy(false)
    }
  }

  // Productos agrupados por categoría, respetando el orden por nombre de las categorías.
  const productGroups = categories
    .map(category => ({ category, items: products.filter(p => p.categoryId === category.id) }))
    .filter(group => group.items.length > 0)

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

            {loadingConfig ? (
              <div className="config-state-center"><div className="config-spinner" /></div>
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

          <section className="config-section">
            <div className="config-section-head">
              <div>
                <h2 className="config-section-title">Productos</h2>
                <p className="config-section-sub">Productos del menú agrupados por categoría</p>
              </div>
              {isAdmin && (
                <button className="config-new-btn" onClick={openProductCreate}>+ Nuevo producto</button>
              )}
            </div>

            {loadingConfig ? (
              <div className="config-state-center"><div className="config-spinner" /></div>
            ) : productGroups.length === 0 ? (
              <div className="config-state-center">
                <p className="config-empty">Aún no hay productos cargados.</p>
              </div>
            ) : (
              <div className="menu-products">
                {productGroups.map(({ category, items }) => {
                  const isCollapsed = collapsed.has(category.id)
                  return (
                    <div className="menu-group" key={category.id}>
                      <button
                        className="menu-group-head"
                        onClick={() => toggleCollapse(category.id)}
                        aria-expanded={!isCollapsed}
                      >
                        <span className={`menu-group-chevron${isCollapsed ? ' menu-group-chevron--collapsed' : ''}`}>
                          <ChevronIcon />
                        </span>
                        <span className="menu-group-title">{category.name}</span>
                        <span className="menu-group-count">({items.length})</span>
                      </button>

                      {!isCollapsed && (
                        <div className="menu-product-list">
                          {items.map(p => (
                            <div className="menu-product-row" key={p.id}>
                              {p.imageUrl ? (
                                <img className="menu-product-img" src={p.imageUrl} alt={p.name} />
                              ) : (
                                <span className="menu-product-img-placeholder" aria-hidden="true">
                                  <ImagePlaceholderIcon />
                                </span>
                              )}
                              <div className="menu-product-info">
                                <span className="menu-product-name">{p.name}</span>
                                <span className="menu-product-price">{formatCurrency(p.price)}</span>
                              </div>
                              {isManager && (
                                <div className="menu-product-toggle">
                                  <ToggleSwitch
                                    checked={!!availability[p.id]}
                                    onChange={() => toggleAvailability(p)}
                                  />
                                </div>
                              )}
                              {isAdmin && (
                                <div className="config-actions-cell">
                                  <button
                                    className="config-dots-btn"
                                    onClick={(e) => toggleProductMenu(e, p.id)}
                                    aria-label={`Acciones para ${p.name}`}
                                    aria-haspopup="true"
                                    aria-expanded={productMenuId === p.id}
                                  >
                                    <DotsIcon />
                                  </button>
                                  {productMenuId === p.id && (
                                    <div className={`config-menu${productMenuUp ? ' config-menu--up' : ''}`} role="menu">
                                      <button className="config-menu-item" onClick={() => openProductEdit(p)}>
                                        Editar
                                      </button>
                                      <button className="config-menu-item config-menu-item--danger" onClick={() => openProductDelete(p)}>
                                        Eliminar
                                      </button>
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </section>
        </div>
      </div>

      {/* Cierra el menú de acciones al hacer click fuera. */}
      {menuId !== null && (
        <div className="config-menu-backdrop" onClick={() => setMenuId(null)} aria-hidden="true" />
      )}

      {productMenuId !== null && (
        <div className="config-menu-backdrop" onClick={() => setProductMenuId(null)} aria-hidden="true" />
      )}

      {drawer && (
        <CategoryDrawer
          open
          mode={drawer.mode}
          category={drawer.category}
          onClose={() => setDrawer(null)}
          onSaved={reloadCategories}
        />
      )}

      {confirm && (
        <div className="config-overlay" role="dialog" aria-modal="true" aria-labelledby="menu-confirm-title">
          <div className="config-modal">
            {confirmStep === 1 ? (
              <>
                <p className="config-modal-title" id="menu-confirm-title">¿Eliminar categoría?</p>
                <p className="config-modal-body">
                  La categoría «{confirm.name}» será eliminada.
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
                    onClick={handleConfirmFirstStep}
                    disabled={confirmBusy}
                  >
                    {confirmBusy ? 'Eliminando…' : 'Eliminar'}
                  </button>
                </div>
              </>
            ) : (
              <>
                <p className="config-modal-title" id="menu-confirm-title">¿Eliminar también los productos?</p>
                <p className="config-modal-body">
                  La categoría «{confirm.name}» tiene {confirm.productCount}{' '}
                  {confirm.productCount === 1 ? 'producto asociado' : 'productos asociados'}. Al
                  eliminarla {confirm.productCount === 1 ? 'también se eliminará' : 'también se eliminarán'}.
                  Esta acción no se puede deshacer.
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
                    {confirmBusy ? 'Eliminando…' : 'Eliminar todo'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {productDrawer && (
        <ProductDrawer
          open
          mode={productDrawer.mode}
          product={productDrawer.product}
          categories={categories}
          onClose={() => setProductDrawer(null)}
          onSaved={() => { reloadProducts(); reloadCategories() }}
        />
      )}

      {productConfirm && (
        <div className="config-overlay" role="dialog" aria-modal="true" aria-labelledby="menu-product-confirm-title">
          <div className="config-modal">
            <p className="config-modal-title" id="menu-product-confirm-title">¿Eliminar producto?</p>
            <p className="config-modal-body">
              El producto «{productConfirm.name}» será eliminado.
            </p>
            {productConfirmError && <p className="config-modal-error">{productConfirmError}</p>}
            <div className="config-modal-actions">
              <button
                className="config-modal-btn config-modal-btn--secondary"
                onClick={() => setProductConfirm(null)}
                disabled={productConfirmBusy}
              >
                Cancelar
              </button>
              <button
                className="config-modal-btn config-modal-btn--danger"
                onClick={handleProductDelete}
                disabled={productConfirmBusy}
              >
                {productConfirmBusy ? 'Eliminando…' : 'Eliminar'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
