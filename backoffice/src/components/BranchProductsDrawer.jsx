import { useState, useEffect, useMemo } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { fetchBranchProducts, updateBranchProductsAvailability } from '../services/branchService'
import './StaffUserDrawer.css'
import './BranchProductsDrawer.css'

// Checkbox con estado indeterminado (seleccionar todo general / por categoría).
function SelectAllCheckbox({ checked, indeterminate, onChange, label }) {
  return (
    <label className="bpd-selectall">
      <input
        type="checkbox"
        checked={checked}
        ref={el => { if (el) el.indeterminate = !checked && indeterminate }}
        onChange={e => onChange(e.target.checked)}
      />
      <span>{label}</span>
    </label>
  )
}

/**
 * US-15-F-08: gestión de disponibilidad de productos para una sucursal.
 * Lista todos los productos agrupados por categoría, precargados con su estado real.
 * Los cambios se acumulan localmente y se guardan con "Guardar cambios", que dispara
 * el PATCH bulk (US-15-07) solo con el diff respecto de la carga.
 */
export default function BranchProductsDrawer({ branch, onClose }) {
  const { token } = useAuth()
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [products, setProducts] = useState([])   // [{productId, name, categoryId, categoryName, imageUrl, available}]
  const [original, setOriginal] = useState({})   // { [productId]: bool } estado al cargar
  const [selected, setSelected] = useState({})   // { [productId]: bool } estado actual
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    fetchBranchProducts(branch.id, token)
      .then(list => {
        if (cancelled) return
        setProducts(list)
        const map = {}
        for (const p of list) map[p.productId] = !!p.available
        setOriginal(map)
        setSelected({ ...map })
      })
      .catch(err => { if (!cancelled) setLoadError(err?.message ?? 'No se pudieron cargar los productos.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [branch.id, token])

  // Agrupa por categoría respetando el orden que ya trae el backend (categoría, nombre).
  const groups = useMemo(() => {
    const byCat = new Map()
    for (const p of products) {
      if (!byCat.has(p.categoryId)) {
        byCat.set(p.categoryId, { categoryId: p.categoryId, categoryName: p.categoryName, items: [] })
      }
      byCat.get(p.categoryId).items.push(p)
    }
    return [...byCat.values()]
  }, [products])

  const allIds = useMemo(() => products.map(p => p.productId), [products])
  const dirty = useMemo(
    () => allIds.some(id => selected[id] !== original[id]),
    [allIds, selected, original],
  )

  function setOne(productId, value) {
    setSaveSuccess(false)
    setSelected(prev => ({ ...prev, [productId]: value }))
  }

  function setMany(ids, value) {
    setSaveSuccess(false)
    setSelected(prev => {
      const next = { ...prev }
      for (const id of ids) next[id] = value
      return next
    })
  }

  function handleClose() {
    if (saving) return
    onClose()
  }

  async function handleSave() {
    setSaving(true)
    setSaveError(null)
    setSaveSuccess(false)
    // Diff: solo los productos que cambiaron respecto de la carga.
    const toEnable = []
    const toDisable = []
    for (const id of allIds) {
      if (selected[id] !== original[id]) (selected[id] ? toEnable : toDisable).push(id)
    }
    try {
      const calls = []
      if (toEnable.length) calls.push(updateBranchProductsAvailability(branch.id, toEnable, true, token))
      if (toDisable.length) calls.push(updateBranchProductsAvailability(branch.id, toDisable, false, token))
      await Promise.all(calls)
      setOriginal({ ...selected })   // los cambios pasan a ser el nuevo baseline
      setSaveSuccess(true)
    } catch (err) {
      // apiFetch ya emitió un toast con el mensaje del backend; lo repetimos inline.
      // Incluye el caso 422 de sucursal desactivada.
      setSaveError(err?.message ?? 'No se pudieron guardar los cambios.')
    } finally {
      setSaving(false)
    }
  }

  const allChecked = allIds.length > 0 && allIds.every(id => selected[id])
  const someChecked = allIds.some(id => selected[id])

  const drawer = (
    <div className="sud-backdrop" onClick={handleClose}>
      <aside
        className="sud-panel bpd-panel"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={`Gestionar productos de ${branch.name}`}
      >
        <div className="sud-header">
          <div>
            <h2 className="sud-title">Gestionar productos</h2>
            <p className="bpd-subtitle">{branch.name}</p>
          </div>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <div className="bpd-body">
          {loading ? (
            <p className="bpd-state">Cargando productos…</p>
          ) : loadError ? (
            <p className="bpd-state sud-error">{loadError}</p>
          ) : products.length === 0 ? (
            <p className="bpd-state">Esta sucursal no tiene productos.</p>
          ) : (
            <>
              <SelectAllCheckbox
                label="Seleccionar todo"
                checked={allChecked}
                indeterminate={someChecked}
                onChange={v => setMany(allIds, v)}
              />

              {groups.map(g => {
                const ids = g.items.map(i => i.productId)
                const catAll = ids.every(id => selected[id])
                const catSome = ids.some(id => selected[id])
                const catOn = ids.filter(id => selected[id]).length
                return (
                  <div className="bpd-group" key={g.categoryId}>
                    <div className="bpd-group-head">
                      <SelectAllCheckbox
                        label={g.categoryName}
                        checked={catAll}
                        indeterminate={catSome}
                        onChange={v => setMany(ids, v)}
                      />
                      <span className="bpd-group-count">{catOn}/{ids.length}</span>
                    </div>
                    <div className="bpd-list">
                      {g.items.map(p => (
                        <label className="bpd-row" key={p.productId}>
                          <input
                            type="checkbox"
                            checked={!!selected[p.productId]}
                            onChange={e => setOne(p.productId, e.target.checked)}
                          />
                          {p.imageUrl
                            ? <img className="bpd-img" src={p.imageUrl} alt="" />
                            : <span className="bpd-img bpd-img--empty" aria-hidden="true" />}
                          <span className="bpd-name">{p.name}</span>
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
            </>
          )}
        </div>

        <div className="bpd-footer">
          {saveError && <p className="sud-error">{saveError}</p>}
          {saveSuccess && <p className="bpd-success">Cambios guardados.</p>}
          <div className="sud-actions">
            <button type="button" className="sud-cancel" onClick={handleClose} disabled={saving}>
              Cerrar
            </button>
            <button
              type="button"
              className="sud-submit"
              onClick={handleSave}
              disabled={saving || !dirty || loading || !!loadError}
            >
              {saving ? 'Guardando…' : 'Guardar cambios'}
            </button>
          </div>
        </div>
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
