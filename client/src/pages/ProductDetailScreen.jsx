import { useState, useEffect, useRef } from 'react'
import { ProductOptions, OptionGroup, OptionAccordion, OptionRadioList } from '../components/ProductOptions'
import { halfAndHalfPrice, sizeLabel, sizedCartId } from '../utils/halfAndHalf'

// Cuánto queda visible el "¡Agregado!" antes de volver al menú.
const CLOSE_DELAY_MS = 1000

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

export function ProductDetailScreen({
  product,
  onBack,
  onAddToCart,
  onAddHalfAndHalf,
  onAddSized,
  getCartQty = () => 0,
  onUpdateCartQty,
  onRemoveFromCart,
}) {
  // Cantidad ya en el carrito para una variante. "Grande" es el producto suelto; los tamaños
  // alternativos tienen id propio para no fusionarse entre sí ni con el entero.
  const cartQtyOf = (size) => getCartQty(size ? sizedCartId(product.id, size.id) : product.id)

  // El stepper arranca en la cantidad que esa variante ya tiene en el carrito. Sin línea
  // previa arranca en 1: no tiene sentido "agregar 0".
  const [qty, setQty] = useState(() => cartQtyOf(null) || 1)
  // Confirmación visible antes de volver al menú: el CTA muestra el resultado durante un
  // segundo y recién ahí se cierra el detalle. Guarda el texto en vez de derivarlo, porque
  // la acción cambia el carrito y el estado derivado ya no describe lo que acaba de pasar
  // (tras agregar, la línea existe y "agregado" se leería como "actualizado").
  const [confirmation, setConfirmation] = useState(null)
  const closeTimer = useRef(null)
  const [halfAndHalf, setHalfAndHalf] = useState(false)
  const [secondHalfId, setSecondHalfId] = useState(null)
  // US-SIZE-F-02: null = "Grande", que es el comportamiento por defecto (precio base del
  // producto y sin productSizeId). Sólo los tamaños alternativos llevan id.
  const [sizeId, setSizeId] = useState(null)

  // Si el cliente vuelve con la flecha durante la confirmación, el timer no debe cerrar un
  // detalle que ya se cerró.
  useEffect(() => () => clearTimeout(closeTimer.current), [])

  // US-HH-F-01: la opción no reemplaza el flujo normal — extiende el mismo CTA. Sólo aparece
  // si la categoría del producto lo permite (allowsHalfAndHalf del menú) y hay al menos otro
  // producto disponible de esa categoría con el que combinar.
  const halfCandidates = product.halfAndHalfCandidates ?? []
  const canOrderHalfAndHalf = product.allowsHalfAndHalf === true && halfCandidates.length > 0
  const secondHalf = halfCandidates.find(c => c.id === secondHalfId) ?? null

  // US-SIZE-F-02: "Grande" no es una fila de product_size — es la ausencia de tamaño. Va
  // primero y es la opción por defecto. Una fila GRANDE cargada por el ADMIN sería un
  // duplicado de esa opción, así que no se ofrece (ver nota en el informe de la historia).
  const sizes = (product.sizes ?? []).filter(s => s.size !== 'GRANDE')
  // El grupo se muestra en toda la categoría que admite tamaños, tenga o no alternativas
  // cargadas este producto: si desapareciera, el cliente no se enteraría de que los tamaños
  // existen y leería este producto como si nunca hubiera opción.
  const showSizes = product.allowsSizes === true
  // Sin alternativas, "Grande" queda fijo: se ve elegido pero no se puede cambiar.
  const hasAlternativeSizes = sizes.length > 0
  const selectedSize = sizes.find(s => s.id === sizeId) ?? null

  // US-SIZE-03: el backend rechaza con 422 un ítem que combine tamaño con mitad y mitad, y
  // "Grande" es justamente el ítem sin tamaño. Por eso mitad y mitad sólo vive en Grande.
  const halfAndHalfBlockedBySize = selectedSize != null

  function handleSelectSize(nextSizeId) {
    setSizeId(nextSizeId)
    // El stepper se recalcula para la variante nueva: muestra lo que esa otra línea ya tiene
    // en el carrito, o 1 si todavía no existe.
    const nextSize = sizes.find(s => s.id === nextSizeId) ?? null
    setQty(cartQtyOf(nextSize) || 1)
    // Pasar a un tamaño alternativo cierra y limpia mitad y mitad: son excluyentes.
    if (nextSizeId != null) {
      setHalfAndHalf(false)
      setSecondHalfId(null)
    }
  }

  // Al colapsar el acordeón se limpia la selección: volver a expandirlo no debe arrastrar la
  // mitad elegida antes.
  //
  // El stepper también se reinicia: armar una combinación es siempre un alta nueva, así que
  // no debe arrastrar las unidades que la variante base tuviera en el carrito. Al colapsar
  // sin confirmar, vuelve a reflejar el carrito real — mismo criterio que el cambio de
  // variante.
  function handleToggleHalfAndHalf(expanded) {
    setHalfAndHalf(expanded)
    if (expanded) {
      setQty(1)
    } else {
      setSecondHalfId(null)
      setQty(cartQtyOf(selectedSize) || 1)
    }
  }

  // Elegir la otra mitad deja el stepper en el mismo estado de alta nueva. Ajustar la
  // cantidad antes de elegir no tiene sentido —el CTA está bloqueado hasta entonces—, así
  // que reiniciarlo acá no pisa nada que el cliente haya decidido.
  function handleSelectSecondHalf(candidateId) {
    setSecondHalfId(candidateId)
    setQty(1)
  }

  const isHalfAndHalfReady = halfAndHalf && secondHalf != null
  // Falta elegir la otra mitad: el CTA se bloquea en vez de agregar el producto entero por
  // error, que sería lo contrario a lo que el cliente marcó.
  const ctaBlocked = halfAndHalf && !secondHalf

  // Mitad y mitad no se ata al carrito: cada combinación es un alta nueva, no la edición de
  // una línea existente. Aplica desde que se abre el acordeón, no recién al elegir la otra
  // mitad, para que el CTA no ofrezca "confirmar cambio" sobre una línea que no se va a
  // tocar. El resto de las variantes sí edita la línea que ya tengan.
  const cartQty = halfAndHalf ? 0 : cartQtyOf(selectedSize)
  const editingExisting = cartQty > 0
  // Sólo se puede bajar a 0 cuando hay algo que quitar; si no, el mínimo es 1.
  const minQty = editingExisting ? 0 : 1
  const removing = editingExisting && qty === 0

  // Funciones planas: dependen de minQty, que cambia con la variante seleccionada.
  const increment = () => setQty(q => Math.min(99, q + 1))
  const decrement = () => setQty(q => Math.max(minQty, q - 1))

  const ctaLabel = ctaBlocked
    ? 'Elegí la otra mitad'
    : removing
      ? 'Quitar del carrito'
      : editingExisting
        ? 'Confirmar cambio'
        : 'Agregar al carrito'

  const currentCartId = selectedSize ? sizedCartId(product.id, selectedSize.id) : product.id

  // Función plana, no useCallback: depende de valores derivados en cada render (selectedSize,
  // secondHalf) que el compilador de React no puede memoizar de forma estable.
  function handleAdd() {
    // `confirmation` bloquea el doble alta: durante el segundo de confirmación el botón sigue
    // en pantalla, y sin esto un segundo toque volvería a aplicar el cambio.
    if (ctaBlocked || confirmation) return
    if (isHalfAndHalfReady) onAddHalfAndHalf?.(product, secondHalf, qty)
    else if (removing) onRemoveFromCart?.(currentCartId)
    else if (editingExisting) onUpdateCartQty?.(currentCartId, qty)
    else if (selectedSize) onAddSized?.(product, selectedSize, qty)
    else onAddToCart?.(product, qty)
    // El mensaje describe la acción recién ejecutada, no el estado posterior del carrito.
    setConfirmation(removing ? '¡Quitado!' : editingExisting ? '¡Actualizado!' : '¡Agregado!')
    closeTimer.current = setTimeout(() => onBack?.(), CLOSE_DELAY_MS)
  }

  // Preview de precio: con la combinación armada vale el mayor de las dos mitades (misma
  // regla que resuelve el backend, US-HH-03). Sin combinación, el precio del producto.
  const unitPrice = isHalfAndHalfReady
    ? halfAndHalfPrice(product, secondHalf)
    : (selectedSize ? Number(selectedSize.price) : product.price)
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

          {(showSizes || canOrderHalfAndHalf) && (
            <ProductOptions>
              {/* US-SIZE-F-02: el tamaño va primero — condiciona si mitad y mitad se puede. */}
              {showSizes && (
                <OptionGroup title="Tamaño">
                  <OptionRadioList
                    name="product-size"
                    legend={hasAlternativeSizes
                      ? 'Elegí el tamaño:'
                      : 'Esta pizza va sólo en tamaño grande:'}
                    options={[
                      { id: null, label: 'Grande', hint: formatPrice(product.price) },
                      ...sizes.map(sz => ({
                        id: sz.id,
                        label: sizeLabel(sz.size),
                        hint: formatPrice(sz.price),
                      })),
                    ]}
                    selectedId={sizeId}
                    onSelect={handleSelectSize}
                    disabled={!hasAlternativeSizes}
                  />
                </OptionGroup>
              )}
              {canOrderHalfAndHalf && (
              <OptionAccordion
                id="option-half-and-half"
                label="Pedir mitad y mitad"
                expanded={halfAndHalf}
                onToggle={handleToggleHalfAndHalf}
                disabled={halfAndHalfBlockedBySize}
                disabledReason="Sólo disponible en tamaño grande"
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
                  onSelect={handleSelectSecondHalf}
                />
              </OptionAccordion>
              )}
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
                disabled={qty <= minQty}
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
            className={`detail-cta-btn${confirmation ? ' detail-cta-btn--added' : ''}`}
            onClick={handleAdd}
            disabled={ctaBlocked}
          >
            {confirmation ? <span className="detail-cta-check">✓</span> : <CartIcon />}
            <span className="detail-cta-label">{confirmation ?? ctaLabel}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
