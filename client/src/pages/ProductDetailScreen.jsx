import { useState, useCallback } from 'react'
import { ProductOptions, OptionAccordion, OptionRadioList } from '../components/ProductOptions'
import { halfAndHalfPrice } from '../utils/halfAndHalf'

function formatPrice(price) {
  return `$${Number(price).toLocaleString('es-AR')}`
}

function getDisplayName(product) {
  const cat = (product.categoryName || '').toLowerCase()
  if (cat.includes('pizza')) return `PIZZA ${product.name}`
  if (cat.includes('empanada')) return `EMPANADA DE ${product.name}`
  return product.name
}

function BackArrowIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M19 12H5M5 12l7-7M5 12l7 7" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

function CartIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <line x1="3" y1="6" x2="21" y2="6" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M16 10a4 4 0 01-8 0" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

function SizeIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
      <path d="M12 6v6l4 2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

export function ProductDetailScreen({ product, onBack, onAddToCart, onAddHalfAndHalf }) {
  const [qty, setQty] = useState(1)
  const [added, setAdded] = useState(false)
  const [halfAndHalf, setHalfAndHalf] = useState(false)
  const [secondHalfId, setSecondHalfId] = useState(null)

  const increment = useCallback(() => setQty(q => Math.min(99, q + 1)), [])
  const decrement = useCallback(() => setQty(q => Math.max(1, q - 1)), [])

  // US-HH-F-01: la opción no reemplaza el flujo normal — extiende el mismo CTA. Sólo aparece
  // si la categoría del producto lo permite (allowsHalfAndHalf del menú) y hay al menos otro
  // producto disponible de esa categoría con el que combinar.
  const halfCandidates = product.halfAndHalfCandidates ?? []
  const canOrderHalfAndHalf = product.allowsHalfAndHalf === true && halfCandidates.length > 0
  const secondHalf = halfCandidates.find(c => c.id === secondHalfId) ?? null

  // Al colapsar el acordeón se limpia la selección: volver a expandirlo no debe arrastrar la
  // mitad elegida antes.
  const handleToggleHalfAndHalf = useCallback((expanded) => {
    setHalfAndHalf(expanded)
    if (!expanded) setSecondHalfId(null)
  }, [])

  const isHalfAndHalfReady = halfAndHalf && secondHalf != null
  // Falta elegir la otra mitad: el CTA se bloquea en vez de agregar el producto entero por
  // error, que sería lo contrario a lo que el cliente marcó.
  const ctaBlocked = halfAndHalf && !secondHalf

  const handleAdd = useCallback(() => {
    if (ctaBlocked) return
    if (isHalfAndHalfReady) onAddHalfAndHalf?.(product, secondHalf, qty)
    else onAddToCart?.(product, qty)
    setAdded(true)
    setTimeout(() => setAdded(false), 1800)
  }, [ctaBlocked, isHalfAndHalfReady, onAddHalfAndHalf, onAddToCart, product, secondHalf, qty])

  // Preview de precio: con la combinación armada vale el mayor de las dos mitades (misma
  // regla que resuelve el backend, US-HH-03). Sin combinación, el precio del producto.
  const unitPrice = isHalfAndHalfReady ? halfAndHalfPrice(product, secondHalf) : product.price
  const totalPrice = unitPrice * qty

  return (
    <div className="detail-screen">
      <div className="detail-hero">
        {product.imageUrl ? (
          <img
            src={product.imageUrl}
            alt={product.name}
            className="detail-hero-img"
          />
        ) : (
          <div className="detail-hero-placeholder" aria-hidden="true" />
        )}
        <div className="detail-hero-gradient" aria-hidden="true" />
        <div className="detail-hero-gold-line" aria-hidden="true" />
        <button
          className="detail-back-btn"
          onClick={onBack}
          aria-label="Volver al menú"
        >
          <BackArrowIcon />
        </button>
      </div>

      <div className="detail-panel">
        <div className="detail-info">
          <h1 className="detail-product-name">{getDisplayName(product)}</h1>
          <div className="detail-size-row">
            <SizeIcon />
            <span className="detail-size-text">Grande · 8 porciones</span>
          </div>
          {product.description && (
            <p className="detail-description">{product.description}</p>
          )}

          {canOrderHalfAndHalf && (
            <ProductOptions>
              {/* US-SIZE-F-02: el grupo de tamaños entra acá como OptionGroup hermano. */}
              <OptionAccordion
                id="option-half-and-half"
                label="Pedir mitad y mitad"
                expanded={halfAndHalf}
                onToggle={handleToggleHalfAndHalf}
              >
                <OptionRadioList
                  name="second-half"
                  legend={`Primera mitad: ½ ${product.name}. Elegí la otra mitad:`}
                  options={halfCandidates.map(c => ({
                    id: c.id,
                    label: `½ ${c.name}`,
                    hint: formatPrice(c.price),
                  }))}
                  selectedId={secondHalfId}
                  onSelect={setSecondHalfId}
                />
              </OptionAccordion>
            </ProductOptions>
          )}
        </div>

        <div className="detail-actions">
          <div className="detail-separator" aria-hidden="true" />
          <div className="detail-price-qty-row">
            <div className="detail-price-block">
              <span className="detail-price" data-testid="detail-total-price">
                {formatPrice(totalPrice)}
              </span>
              {qty > 1 && (
                <span className="detail-unit-price">{formatPrice(unitPrice)} c/u</span>
              )}
            </div>
            <div className="detail-qty-control" role="group" aria-label="Cantidad">
              <button
                className="detail-qty-btn"
                onClick={decrement}
                disabled={qty <= 1}
                aria-label="Reducir cantidad"
              >
                −
              </button>
              <span className="detail-qty-number" aria-live="polite">
                {String(qty).padStart(2, '0')}
              </span>
              <button
                className="detail-qty-btn"
                onClick={increment}
                aria-label="Aumentar cantidad"
              >
                +
              </button>
            </div>
          </div>
          <button
            className={`detail-cta-btn${added ? ' detail-cta-btn--added' : ''}`}
            onClick={handleAdd}
            disabled={ctaBlocked}
          >
            {added ? (
              <span className="detail-cta-check">✓</span>
            ) : (
              <CartIcon />
            )}
            <span className="detail-cta-label">
              {added
                ? '¡Agregado!'
                : ctaBlocked
                  ? 'Elegí la otra mitad'
                  : 'Agregar al carrito'}
            </span>
          </button>
        </div>
      </div>
    </div>
  )
}
