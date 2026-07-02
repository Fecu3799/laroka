import { useState, useEffect, useCallback } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import {
  createProduct,
  updateProduct,
  fetchProductBranchConfig,
  updateProductBranchConfig,
  updateProductPrice,
} from '../services/catalogService'
import { formatCurrency } from '../utils/shiftsUtils'
import CustomSelect from './CustomSelect'
import ToggleSwitch from './ToggleSwitch'
import ImageUploader from './ui/ImageUploader'
import './StaffUserDrawer.css'
import './ProductDrawer.css'

const EMPTY_FORM = { name: '', description: '', categoryId: '', price: '', imageUrl: '' }

// Normaliza un valor numérico a string para el input de precio (sin decimales sobrantes).
function priceStr(value) {
  if (value == null) return ''
  return String(Number(value))
}

export default function ProductDrawer({ open, mode, product, categories, onClose, onSaved }) {
  const { token, tenantId, role, branchId } = useAuth()
  const isEdit = mode === 'edit'
  const isAdmin = role === 'ADMIN'

  const [form, setForm] = useState(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  // ── Configuración por sucursal (US-14-F-03) ─────────────────────
  const [config, setConfig] = useState([])
  const [priceDraft, setPriceDraft] = useState({})   // branchId → string del input
  const [savingRows, setSavingRows] = useState(() => new Set())
  const [bcLoading, setBcLoading] = useState(false)
  const [bcError, setBcError] = useState(false)
  const [bcRowError, setBcRowError] = useState(null)
  const [levelOpen, setLevelOpen] = useState(false)
  const [levelBusy, setLevelBusy] = useState(false)
  const [levelError, setLevelError] = useState(null)

  // Precio base persistido del producto: referencia para limpiar el override.
  const productId = product?.id ?? null
  const basePrice = product?.price != null ? Number(product.price) : null

  const loadBranchConfig = useCallback(() => {
    if (!productId || !token) return
    setBcLoading(true)
    setBcError(false)
    setBcRowError(null)
    fetchProductBranchConfig(productId, token)
      .then(rows => {
        setConfig(rows)
        setPriceDraft(Object.fromEntries(rows.map(r => [r.branchId, priceStr(r.effectivePrice)])))
      })
      .catch(() => setBcError(true))
      .finally(() => setBcLoading(false))
  }, [productId, token])

  // Precarga / reset al abrir.
  useEffect(() => {
    if (!open) return
    setError(null)
    if (isEdit && product) {
      setForm({
        name: product.name ?? '',
        description: product.description ?? '',
        categoryId: product.categoryId != null ? String(product.categoryId) : '',
        price: product.price != null ? String(product.price) : '',
        imageUrl: product.imageUrl ?? '',
      })
    } else {
      setForm(EMPTY_FORM)
    }
  }, [open, isEdit, product])

  // La configuración por sucursal solo existe en edición (un producto nuevo no la
  // tiene hasta guardarse). Se carga al abrir el drawer de edición.
  useEffect(() => {
    if (!open || !isEdit || !productId) {
      setConfig([])
      setPriceDraft({})
      setBcError(false)
      setBcRowError(null)
      return
    }
    loadBranchConfig()
  }, [open, isEdit, productId, loadBranchConfig])

  function setField(key, value) {
    setForm(prev => ({ ...prev, [key]: value }))
  }

  function handleClose() {
    if (submitting) return
    onClose()
  }

  const nameValid = form.name.trim().length > 0
  const categoryValid = form.categoryId !== ''
  const priceNumber = Number(form.price)
  const priceValid = form.price !== '' && Number.isFinite(priceNumber) && priceNumber > 0
  const canSubmit = nameValid && categoryValid && priceValid && !submitting

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    setError(null)
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        price: priceNumber,
        imageUrl: form.imageUrl.trim() || null,
        categoryId: Number(form.categoryId),
        tenantId,
      }
      if (isEdit) {
        await updateProduct(product.id, payload, token)
      } else {
        await createProduct(payload, token)
      }
      onSaved()
      onClose()
    } catch (err) {
      // apiFetch ya emite un toast con el mensaje del backend; lo repetimos inline
      // junto al formulario para que quede visible en el drawer.
      setError(err?.message ?? 'No se pudo guardar el producto.')
    } finally {
      setSubmitting(false)
    }
  }

  // MANAGER solo puede editar la sucursal de su token; ADMIN cualquiera.
  function canEditRow(row) {
    if (isAdmin) return true
    if (role === 'MANAGER') return row.branchId === branchId
    return false
  }

  function startSaving(id) {
    setSavingRows(prev => new Set(prev).add(id))
  }

  function stopSaving(id) {
    setSavingRows(prev => {
      const next = new Set(prev)
      next.delete(id)
      return next
    })
  }

  // Confirma la edición inline de precio de una fila al perder foco / Enter.
  function commitPrice(row) {
    const raw = priceDraft[row.branchId] ?? ''
    const value = Number(raw)
    // Inválido → revierte al precio efectivo actual.
    if (raw === '' || !Number.isFinite(value) || value <= 0) {
      setPriceDraft(d => ({ ...d, [row.branchId]: priceStr(row.effectivePrice) }))
      return
    }
    // Si iguala el precio base, el override se limpia (null); si no, es el nuevo override.
    const newOverride = basePrice != null && value === basePrice ? null : value
    const currentOverride = row.priceOverride != null ? Number(row.priceOverride) : null
    // Sin cambios reales → solo normaliza el texto mostrado.
    if (newOverride === currentOverride) {
      setPriceDraft(d => ({ ...d, [row.branchId]: priceStr(value) }))
      return
    }
    startSaving(row.branchId)
    setBcRowError(null)
    updateProductBranchConfig(product.id, { branchId: row.branchId, priceOverride: newOverride }, token)
      .then(() => {
        setConfig(cfg => cfg.map(r =>
          r.branchId === row.branchId ? { ...r, priceOverride: newOverride, effectivePrice: value } : r,
        ))
        setPriceDraft(d => ({ ...d, [row.branchId]: priceStr(value) }))
      })
      .catch(err => {
        setPriceDraft(d => ({ ...d, [row.branchId]: priceStr(row.effectivePrice) }))
        setBcRowError(err?.message ?? 'No se pudo actualizar el precio.')
      })
      .finally(() => stopSaving(row.branchId))
  }

  function handlePriceKeyDown(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      e.target.blur()
    }
  }

  // Toggle de disponibilidad (optimista). Se reenvía el override vigente porque el
  // backend siempre reescribe priceOverride al actualizar la config de sucursal.
  function toggleAvailable(row) {
    const newAvailable = !row.available
    startSaving(row.branchId)
    setBcRowError(null)
    setConfig(cfg => cfg.map(r => (r.branchId === row.branchId ? { ...r, available: newAvailable } : r)))
    updateProductBranchConfig(
      product.id,
      { branchId: row.branchId, available: newAvailable, priceOverride: row.priceOverride ?? null },
      token,
    )
      .catch(err => {
        setConfig(cfg => cfg.map(r => (r.branchId === row.branchId ? { ...r, available: row.available } : r)))
        setBcRowError(err?.message ?? 'No se pudo actualizar la disponibilidad.')
      })
      .finally(() => stopSaving(row.branchId))
  }

  function handleLevelPrice() {
    if (basePrice == null) return
    setLevelBusy(true)
    setLevelError(null)
    updateProductPrice(product.id, { price: basePrice, applyToAllBranches: true }, token)
      .then(() => {
        setLevelOpen(false)
        loadBranchConfig()
        onSaved?.()
      })
      .catch(err => setLevelError(err?.message ?? 'No se pudo igualar el precio.'))
      .finally(() => setLevelBusy(false))
  }

  if (!open) return null

  const drawer = (
    <div className="sud-backdrop" onClick={handleClose}>
      <aside
        className="sud-panel"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={isEdit ? 'Editar producto' : 'Nuevo producto'}
      >
        <div className="sud-header">
          <h2 className="sud-title">{isEdit ? 'Editar producto' : 'Nuevo producto'}</h2>
          <button type="button" className="sud-close" onClick={handleClose} aria-label="Cerrar">×</button>
        </div>

        <form className="sud-form" onSubmit={handleSubmit}>
          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-name">Nombre</label>
            <input
              id="prod-name"
              className="sud-input"
              type="text"
              placeholder="Nombre del producto"
              value={form.name}
              onChange={e => setField('name', e.target.value)}
              autoFocus
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-description">Descripción</label>
            <textarea
              id="prod-description"
              className="sud-input"
              rows={3}
              placeholder="Descripción (opcional)"
              value={form.description}
              onChange={e => setField('description', e.target.value)}
              style={{ resize: 'vertical', minHeight: '78px', fontFamily: 'inherit', lineHeight: 1.5 }}
            />
          </div>

          <div className="sud-field">
            <span className="sud-label" id="prod-category-label">Categoría</span>
            <CustomSelect
              id="prod-category"
              ariaLabelledBy="prod-category-label"
              value={form.categoryId}
              onChange={val => setField('categoryId', val)}
              options={categories.map(c => ({ value: String(c.id), label: c.name }))}
              placeholder="Seleccionar categoría…"
            />
          </div>

          <div className="sud-field">
            <label className="sud-label" htmlFor="prod-price">Precio base</label>
            <input
              id="prod-price"
              className="sud-input"
              type="number"
              min="0"
              step="0.01"
              placeholder="0"
              value={form.price}
              onChange={e => setField('price', e.target.value)}
            />
            {form.price !== '' && !priceValid && (
              <span className="sud-hint">El precio debe ser un número mayor a 0.</span>
            )}
          </div>

          <div className="sud-field">
            <ImageUploader
              label="Imagen"
              value={form.imageUrl || null}
              onChange={url => setField('imageUrl', url)}
              token={token}
              context="products"
              aspectRatio={1}
              helperText="Recomendado: imagen cuadrada"
            />
          </div>

          {error && <p className="sud-error">{error}</p>}

          <div className="sud-actions">
            <button type="button" className="sud-cancel" onClick={handleClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="sud-submit" disabled={!canSubmit}>
              {submitting ? 'Guardando…' : isEdit ? 'Guardar cambios' : 'Crear producto'}
            </button>
          </div>

          {/* ── Configuración por sucursal (US-14-F-03) ── solo en edición ── */}
          {isEdit && (
            <section className="pbc-section">
              <div className="pbc-head">
                <div>
                  <h3 className="pbc-title">Configuración por sucursal</h3>
                  <p className="pbc-sub">Precio y disponibilidad por sucursal. Los cambios se guardan automáticamente.</p>
                </div>
                {isAdmin && (
                  <button
                    type="button"
                    className="pbc-level-btn"
                    onClick={() => { setLevelError(null); setLevelOpen(true) }}
                    disabled={bcLoading || bcError || basePrice == null}
                  >
                    Igualar precio
                  </button>
                )}
              </div>

              {bcLoading ? (
                <div className="pbc-state"><span className="pbc-spinner" />Cargando configuración…</div>
              ) : bcError ? (
                <div className="pbc-state pbc-state--error">No se pudo cargar la configuración por sucursal.</div>
              ) : config.length === 0 ? (
                <div className="pbc-state">No hay sucursales para configurar.</div>
              ) : (
                <div className="pbc-table">
                  <div className="pbc-thead">
                    <span className="pbc-th">Sucursal</span>
                    <span className="pbc-th pbc-th--center">Precio</span>
                    <span className="pbc-th pbc-th--center">Disponible</span>
                  </div>
                  {config.map(row => {
                    const editable = canEditRow(row)
                    const saving = savingRows.has(row.branchId)
                    const hasOverride = row.priceOverride != null
                    return (
                      <div className={`pbc-row${editable ? '' : ' pbc-row--disabled'}`} key={row.branchId}>
                        <span className="pbc-branch" title={row.branchName}>{row.branchName}</span>
                        <div className="pbc-price-wrap">
                          <input
                            className={`pbc-price-input${hasOverride ? ' pbc-price-input--override' : ''}`}
                            type="number"
                            min="0"
                            step="0.01"
                            inputMode="decimal"
                            value={priceDraft[row.branchId] ?? ''}
                            onChange={e => setPriceDraft(d => ({ ...d, [row.branchId]: e.target.value }))}
                            onBlur={() => commitPrice(row)}
                            onKeyDown={handlePriceKeyDown}
                            disabled={!editable || saving}
                            aria-label={`Precio en ${row.branchName}`}
                            title={hasOverride ? 'Precio personalizado para esta sucursal' : undefined}
                          />
                          {hasOverride && <span className="pbc-override-dot" aria-hidden="true" />}
                        </div>
                        <div className="pbc-cell--center">
                          <ToggleSwitch
                            checked={!!row.available}
                            onChange={() => toggleAvailable(row)}
                            disabled={!editable || saving}
                          />
                        </div>
                      </div>
                    )
                  })}
                  {bcRowError && <p className="pbc-row-error">{bcRowError}</p>}
                </div>
              )}
            </section>
          )}
        </form>
      </aside>

      {levelOpen && (
        <div
          className="pbc-modal-overlay"
          onClick={() => { if (!levelBusy) setLevelOpen(false) }}
          role="dialog"
          aria-modal="true"
          aria-labelledby="pbc-level-title"
        >
          <div className="pbc-modal" onClick={e => e.stopPropagation()}>
            <p className="pbc-modal-title" id="pbc-level-title">¿Igualar precio en todas las sucursales?</p>
            <p className="pbc-modal-body">
              Todas las sucursales pasarán a usar el precio base de {formatCurrency(basePrice)}.
              Se eliminarán los precios personalizados de cada sucursal.
            </p>
            {levelError && <p className="pbc-modal-error">{levelError}</p>}
            <div className="pbc-modal-actions">
              <button
                type="button"
                className="pbc-modal-btn pbc-modal-btn--secondary"
                onClick={() => setLevelOpen(false)}
                disabled={levelBusy}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="pbc-modal-btn pbc-modal-btn--primary"
                onClick={handleLevelPrice}
                disabled={levelBusy}
              >
                {levelBusy ? 'Aplicando…' : 'Igualar precio'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
