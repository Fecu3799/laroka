import { useState, useCallback } from 'react'

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

export function ProductDetailScreen({ product, onBack }) {
  const [qty, setQty] = useState(1)
  const [added, setAdded] = useState(false)

  const increment = useCallback(() => setQty(q => Math.min(99, q + 1)), [])
  const decrement = useCallback(() => setQty(q => Math.max(1, q - 1)), [])

  const handleAdd = useCallback(() => {
    setAdded(true)
    setTimeout(() => setAdded(false), 1800)
  }, [])

  const totalPrice = product.price * qty

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
        </div>

        <div className="detail-actions">
          <div className="detail-separator" aria-hidden="true" />
          <div className="detail-price-qty-row">
            <div className="detail-price-block">
              <span className="detail-price">{formatPrice(totalPrice)}</span>
              {qty > 1 && (
                <span className="detail-unit-price">{formatPrice(product.price)} c/u</span>
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
          >
            {added ? (
              <span className="detail-cta-check">✓</span>
            ) : (
              <CartIcon />
            )}
            <span className="detail-cta-label">
              {added ? '¡Agregado!' : 'Agregar al carrito'}
            </span>
          </button>
        </div>
      </div>
    </div>
  )
}
