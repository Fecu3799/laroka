import { useState, useEffect, useRef } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { createBackofficeOrder } from '../services/ordersService'
import { fetchBranchMenu } from '../services/catalogService'
import { fetchBranches } from '../services/branchService'
import { canConfirmOrder, cartItemKey, halfAndHalfUnitPrice, orderItemDisplayName } from '../utils/ordersUtils'

// Único tamaño alternativo. El grande es implícito: es el producto sin productSizeId, con
// su precio base — nunca tiene fila propia en product_size (US-SIZE-04).
const CHICA = 'CHICA'

// Píxeles ocultos por debajo del panel a partir de los cuales vale la pena mostrar la flecha.
// Con una tolerancia de 1px la flecha aparecía con la lista visualmente completa: el padding
// inferior del contenedor y el redondeo de las filas desbordan unos pocos píxeles sin
// esconder nada. Una fila del carrito mide ~40px, así que este umbral queda muy por debajo de
// media fila —no puede tapar un ítem real— pero por encima del ruido de layout.
const SCROLL_HINT_THRESHOLD_PX = 16
import './NewOrderModal.css'

function formatPrice(n) {
  return '$' + Number(n).toLocaleString('es-AR', { maximumFractionDigits: 0 })
}

function TrashIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ChevronDownIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="m6 9 6 6 6-6"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export default function NewOrderModal({ open, onClose }) {
  const { token } = useAuth()
  const { activeBranchId: branchId } = useBranch()

  const [menuCategories,  setMenuCategories]  = useState([])
  const [menuLoading,     setMenuLoading]     = useState(false)
  const [searchQuery,     setSearchQuery]     = useState('')
  const [cartItems,       setCartItems]       = useState([])
  const [orderType,       setOrderType]       = useState('TAKEAWAY')
  const [deliveryAddress, setDeliveryAddress] = useState('')
  const [paymentMethod,   setPaymentMethod]   = useState('CASH')
  const [customerName,    setCustomerName]    = useState('')
  const [customerPhone,   setCustomerPhone]   = useState('')
  const [notes,           setNotes]           = useState('')
  const [submitting,      setSubmitting]      = useState(false)
  const [error,           setError]           = useState(null)
  const [branchDeliveryFee, setBranchDeliveryFee] = useState(0)
  const [branchServiceFee,  setBranchServiceFee]  = useState(0)
  // US-15-F-09: confirmación explícita cuando el pedido manual incluye productos
  // marcados como no disponibles en la sucursal (el backend BACKOFFICE no bloquea).
  const [confirmUnavailable, setConfirmUnavailable] = useState(false)
  // US-HH-F-03: primera mitad elegida, a la espera de la otra. Null = modo normal.
  const [halfPending, setHalfPending] = useState(null)
  // US-SIZE-F-03: productos marcados para agregarse en tamaño chica. El grande es el modo
  // por defecto (sin productSizeId), así que sólo se marca lo que se aparta de él.
  const [chicaMode, setChicaMode] = useState(() => new Set())

  // Indicadores de contenido oculto en el panel de productos seleccionados, uno por borde.
  // Reflejan el estado real del scroll: aparecen sólo si hay algo tapado de ese lado.
  const itemsSectionRef = useRef(null)
  const [hasMoreItemsBelow, setHasMoreItemsBelow] = useState(false)
  const [hasMoreItemsAbove, setHasMoreItemsAbove] = useState(false)

  function resetState() {
    setMenuCategories([])
    setSearchQuery('')
    setCartItems([])
    setOrderType('TAKEAWAY')
    setDeliveryAddress('')
    setPaymentMethod('CASH')
    setCustomerName('')
    setCustomerPhone('')
    setNotes('')
    setError(null)
    setBranchDeliveryFee(0)
    setBranchServiceFee(0)
    setHalfPending(null)
    setChicaMode(new Set())
    setConfirmUnavailable(false)
  }

  function handleClose() {
    if (submitting) return
    resetState()
    onClose()
  }

  useEffect(() => {
    if (!open || !token || !branchId) return
    setMenuLoading(true)
    Promise.all([
      fetchBranchMenu(branchId, token),
      fetchBranches(token).then(list => list.find(b => String(b.id) === String(branchId))),
    ]).then(([menu, branch]) => {
      setMenuCategories(Array.isArray(menu) ? menu : [])
      if (branch) {
        setBranchDeliveryFee(branch.deliveryFee ?? 0)
        setBranchServiceFee(branch.serviceFee ?? 0)
      }
    }).catch(() => {
      setMenuCategories([])
    }).finally(() => {
      setMenuLoading(false)
    })
  }, [open, token, branchId])

  // Recalcula si queda contenido oculto abajo en el panel de productos seleccionados.
  // Depende de cartItems para reevaluar cuando se agrega, quita o cambia la cantidad de un
  // ítem: agregar uno nuevo puede volver a exceder el alto visible y devolver la flecha.
  useEffect(() => {
    const el = itemsSectionRef.current
    if (!el) {
      setHasMoreItemsBelow(false)
      setHasMoreItemsAbove(false)
      return
    }
    function update() {
      const hiddenBelow = el.scrollHeight - (el.scrollTop + el.clientHeight)
      setHasMoreItemsBelow(hiddenBelow > SCROLL_HINT_THRESHOLD_PX)
      // Arriba lo oculto es directamente scrollTop, con el mismo umbral para que las dos
      // flechas aparezcan y desaparezcan con el mismo criterio.
      setHasMoreItemsAbove(el.scrollTop > SCROLL_HINT_THRESHOLD_PX)
    }
    update()
    el.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    return () => {
      el.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
    }
  }, [open, cartItems])

  // Mueve el panel exactamente un ítem, en la dirección indicada (1 = abajo, -1 = arriba).
  // El paso se mide del DOM (distancia entre dos filas, que ya incluye el gap de la lista)
  // en vez de hardcodearse, para no desincronizarse del CSS. La animación la da
  // scroll-behavior: smooth del contenedor.
  function scrollItemsBy(direction) {
    const el = itemsSectionRef.current
    if (!el) return
    const items = el.querySelectorAll('.nom-cart-item')
    let step = items.length > 1
      ? items[1].offsetTop - items[0].offsetTop
      : items[0]?.offsetHeight ?? 0
    // Sin layout medible (jsdom, o una sola fila sin alto) se cae a un paso conservador:
    // moverse de más saltearía ítems, que es peor que quedarse corto.
    if (!step) step = SCROLL_HINT_THRESHOLD_PX * 2
    el.scrollTop += direction * step
  }

  // ── Mitad y mitad (US-HH-F-03) ───────────────────────────────
  //
  // Flujo de dos toques: el primer ½ deja el producto "pendiente"; el segundo toque —sobre
  // el ½ o sobre la fila de otra pizza— cierra la combinación. Mientras hay pendiente, sólo
  // quedan habilitados los productos de la MISMA categoría: el backend valida que ambas
  // mitades compartan tipo de categoría (US-HH-02) y el menú no expone el categoryTypeId,
  // así que la categoría es el único alcance que garantiza que el pedido no vuelva con 422.
  // Un producto no disponible puede ser mitad, igual que puede pedirse entero: el backoffice
  // no lo bloquea, sólo advierte antes de confirmar (US-15-F-09). El combinado hereda la
  // no disponibilidad y cae en esa misma advertencia.
  function isHalfCandidate(product, cat) {
    if (!cat.allowsHalfAndHalf) return false
    // US-SIZE-F-03: un ítem con tamaño no puede ser mitad y mitad — el backend lo rechaza
    // con 422 (US-SIZE-03). Marcar la chica inhabilita el ½ de esa fila.
    if (chicaMode.has(product.id)) return false
    if (!halfPending) return true
    return cat.categoryName === halfPending.categoryName && product.id !== halfPending.product.id
  }

  // ── Tamaño chica (US-SIZE-F-03) ──────────────────────────────
  //
  // El grande es implícito: es el producto sin productSizeId, con su precio base. Sólo la
  // chica se marca, y su precio efectivo de la sucursal ya viene resuelto en sizes[].
  function chicaSizeOf(product) {
    return (product.sizes ?? []).find(s => s.size === CHICA) ?? null
  }

  function canChooseChica(product, cat) {
    return cat.allowsSizes === true && chicaSizeOf(product) != null
  }

  function toggleChica(product) {
    setChicaMode(prev => {
      const next = new Set(prev)
      if (next.has(product.id)) next.delete(product.id)
      else next.add(product.id)
      return next
    })
  }

  function toggleHalfAndHalf(product, cat) {
    if (!halfPending) {
      setHalfPending({ product, categoryName: cat.categoryName })
      return
    }
    // Volver a tocar la misma pizza cancela: es el gesto de "me arrepentí".
    if (product.id === halfPending.product.id) {
      setHalfPending(null)
      return
    }
    if (!isHalfCandidate(product, cat)) return
    addHalfAndHalfToCart(halfPending.product, product)
    setHalfPending(null)
  }

  // Click sobre la fila: con pendiente activo completa la combinación (o la cancela si es la
  // misma pizza); sin pendiente, agrega el producto suelto como siempre.
  function handleProductRowClick(product, cat) {
    if (!halfPending) {
      addToCart(product)
      return
    }
    toggleHalfAndHalf(product, cat)
  }

  // ── Computed ─────────────────────────────────────────────────

  const filteredMenu = menuCategories
    .map(cat => ({
      ...cat,
      products: cat.products.filter(p =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase())
      ),
    }))
    .filter(cat => cat.products.length > 0)

  const subtotal           = cartItems.reduce((s, i) => s + i.unitPrice * i.quantity, 0)
  const computedDeliveryFee = orderType === 'DELIVERY' ? branchDeliveryFee : 0
  const total              = subtotal + computedDeliveryFee + branchServiceFee
  const itemCount          = cartItems.reduce((s, i) => s + i.quantity, 0)
  const canConfirm         = canConfirmOrder({ cartItems, orderType, deliveryAddress })
  // Ítems del carrito marcados como no disponibles en la sucursal (available === false).
  const unavailableItems   = cartItems.filter(i => i.available === false)

  const handleDebugFill = import.meta.env.DEV
    ? () => {
        const next = (parseInt(localStorage.getItem('pedisur_debug_fill_count') || '0', 10)) + 1
        localStorage.setItem('pedisur_debug_fill_count', String(next))
        const ts = new Date().toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
        setCustomerName(`Dev User #${next}`)
        setCustomerPhone('2804000000')
        setDeliveryAddress('Av. Roca 123, Puerto Madryn')
        setNotes(`[DEBUG #${next} · ${ts}]`)
      }
    : null

  // ── Cart operations ──────────────────────────────────────────

  // Alta genérica: `entry` ya viene con productId/secondProductId resueltos. Los ítems se
  // identifican por key (US-HH-F-03) porque un combinado no debe fusionarse con el producto
  // suelto de su primera mitad, que comparte productId.
  function addEntryToCart(entry) {
    const key = cartItemKey(entry)
    setCartItems(prev => {
      const existing = prev.find(i => i.key === key)
      if (existing) {
        return prev.map(i => (i.key === key ? { ...i, quantity: i.quantity + 1 } : i))
      }
      return [...prev, { ...entry, key, quantity: 1 }]
    })
  }

  function addToCart(product) {
    // US-SIZE-F-03: con la chica marcada, el ítem viaja con productSizeId y el precio del
    // tamaño; sin marcar es el producto entero de siempre.
    const chica = chicaMode.has(product.id) ? chicaSizeOf(product) : null
    addEntryToCart({
      productId:     product.id,
      productName:   product.name,
      productSizeId: chica ? chica.id : undefined,
      sizeName:      chica ? chica.size : undefined,
      unitPrice:     chica ? chica.price : product.price,
      available:     product.available,
    })
  }

  // US-HH-F-03: ítem combinado. El precio es el mayor de las dos mitades (US-HH-03) y la
  // disponibilidad, la peor de las dos: si cualquiera está no disponible, el ítem lo está.
  function addHalfAndHalfToCart(first, second) {
    addEntryToCart({
      productId:         first.id,
      productName:       first.name,
      secondProductId:   second.id,
      secondProductName: second.name,
      unitPrice:         halfAndHalfUnitPrice(first.price, second.price),
      available:         first.available !== false && second.available !== false,
    })
  }

  function decreaseQuantity(key) {
    setCartItems(prev => {
      const item = prev.find(i => i.key === key)
      if (!item) return prev
      if (item.quantity <= 1) return prev.filter(i => i.key !== key)
      return prev.map(i => (i.key === key ? { ...i, quantity: i.quantity - 1 } : i))
    })
  }

  function increaseQuantity(key) {
    setCartItems(prev =>
      prev.map(i => (i.key === key ? { ...i, quantity: i.quantity + 1 } : i))
    )
  }

  function removeFromCart(key) {
    setCartItems(prev => prev.filter(i => i.key !== key))
  }

  // ── Submit ───────────────────────────────────────────────────

  function handleConfirm() {
    if (!canConfirm || submitting) return
    // US-15-F-09: si hay productos no disponibles, pedir confirmación explícita
    // antes de disparar el POST. Cancelar vuelve al formulario sin perder datos.
    if (unavailableItems.length > 0) {
      setConfirmUnavailable(true)
      return
    }
    submitOrder()
  }

  async function submitOrder() {
    setConfirmUnavailable(false)
    setSubmitting(true)
    setError(null)
    try {
      await createBackofficeOrder({
        branchId: branchId,
        orderType,
        deliveryAddress: orderType === 'DELIVERY' ? deliveryAddress : null,
        paymentMethod,
        customerName: customerName || null,
        customerPhone: customerPhone || null,
        notes: notes || null,
        // US-HH-F-03: un ítem combinado viaja con secondProductId; el simple, sin el campo.
        items:  cartItems.map(i => ({
          productId: i.productId,
          ...(i.secondProductId != null ? { secondProductId: i.secondProductId } : {}),
          // US-SIZE-F-03: sólo la chica viaja; el grande es la ausencia del campo.
          ...(i.productSizeId != null ? { productSizeId: i.productSizeId } : {}),
          quantity: i.quantity,
        })),
        origin: 'BACKOFFICE',
      }, token)
      window.dispatchEvent(new Event('pedisur:order-created'))
      resetState()
      onClose()
    } catch {
      setError('Error al crear el pedido. Intente nuevamente.')
    } finally {
      setSubmitting(false)
    }
  }

  // ── Render ───────────────────────────────────────────────────

  const modal = (
    <div
      className={`nom-backdrop${open ? ' nom-backdrop--open' : ''}`}
      onClick={handleClose}
      aria-hidden={!open}
    >
      <div
        className="nom-dialog"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Nueva orden"
      >

        {/* Header */}
        <div className="nom-header">
          <div className="nom-header-left">
            <h2 className="nom-title">Nueva orden</h2>
            {itemCount > 0 && (
              <span className="nom-subtitle">
                MANUAL · {itemCount} PRODUCTO{itemCount !== 1 ? 'S' : ''}
              </span>
            )}
          </div>
          <button
            type="button"
            className="nom-close-btn"
            onClick={handleClose}
            disabled={submitting}
            aria-label="Cerrar"
          >
            ×
          </button>
        </div>

        {/* Body */}
        <div className="nom-body">

          {/* Left: catalog */}
          <div className="nom-col-left">
            <div className="nom-search-box">
              <svg className="nom-search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="2" />
                <path d="m21 21-4.35-4.35" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <input
                className="nom-search-input"
                type="text"
                placeholder="Buscar producto..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
            </div>

            {/* US-HH-F-03: estado de la combinación en curso + salida explícita. */}
            {halfPending && (
              <div className="nom-half-hint" role="status">
                <span>
                  ½ {halfPending.product.name} — elegí la otra mitad
                </span>
                <button
                  type="button"
                  className="nom-half-hint-cancel"
                  onClick={() => setHalfPending(null)}
                >
                  Cancelar
                </button>
              </div>
            )}

            <div className="nom-catalog">
              {menuLoading ? (
                <div className="nom-catalog-state">Cargando menú...</div>
              ) : filteredMenu.length === 0 ? (
                <div className="nom-catalog-state">
                  {searchQuery ? 'Sin resultados' : 'Menú no disponible'}
                </div>
              ) : (
                filteredMenu.map(cat => (
                  <div key={cat.categoryName} className="nom-category">
                    <div className="nom-category-header">
                      <span className="nom-category-name">{cat.categoryName.toUpperCase()}</span>
                      <span className="nom-category-count">{cat.products.length}</span>
                    </div>
                    {cat.products.map(product => {
                      const isPendingHalf = halfPending?.product.id === product.id
                      // US-SIZE-F-03: con la chica marcada, la fila opera sobre ese tamaño y
                      // el precio mostrado sigue al ítem que realmente se agrega.
                      const isChica = chicaMode.has(product.id)
                      const chica = isChica ? chicaSizeOf(product) : null
                      // Con una mitad pendiente, todo lo que no sirve para completarla queda
                      // fuera de juego (salvo la propia pendiente, que cancela al tocarla).
                      const dimmed = halfPending != null && !isPendingHalf && !isHalfCandidate(product, cat)
                      return (
                        <div
                          key={product.id}
                          className={`nom-product-row${product.available === false ? ' nom-product-row--unavailable' : ''}`
                            + `${dimmed ? ' nom-product-row--dimmed' : ''}`
                            + `${isPendingHalf ? ' nom-product-row--half-pending' : ''}`}
                          onClick={dimmed ? undefined : () => handleProductRowClick(product, cat)}
                          aria-disabled={dimmed || undefined}
                        >
                          <div className="nom-product-info">
                            <span className="nom-product-name">
                              {product.name}
                              {product.available === false && (
                                <span className="nom-product-badge" title="Producto marcado como no disponible">
                                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                    <path d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"
                                      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                                  </svg>
                                  No disponible
                                </span>
                              )}
                            </span>
                            {product.description && (
                              <span className="nom-product-desc">{product.description}</span>
                            )}
                          </div>
                          <span className="nom-product-price">
                            {formatPrice(chica ? chica.price : product.price)}
                          </span>
                          {/* US-SIZE-F-03: marca la fila para agregarse en tamaño chica. El
                              grande no se marca — es el comportamiento por defecto. */}
                          {canChooseChica(product, cat) && (
                            <button
                              type="button"
                              className={`nom-size-btn${isChica ? ' nom-size-btn--active' : ''}`}
                              onClick={e => { e.stopPropagation(); toggleChica(product) }}
                              disabled={dimmed}
                              aria-pressed={isChica}
                              aria-label={
                                isChica
                                  ? `Volver a tamaño grande para ${product.name}`
                                  : `Elegir tamaño chica para ${product.name}`
                              }
                            >
                              CH
                            </button>
                          )}
                          {/* US-HH-F-03: sólo en categorías que admiten mitad y mitad. Un
                              producto no disponible también puede ser mitad — se advierte
                              al confirmar, igual que si se pidiera entero. */}
                          {cat.allowsHalfAndHalf && (
                            <button
                              type="button"
                              className={`nom-half-btn${isPendingHalf ? ' nom-half-btn--active' : ''}`}
                              onClick={e => { e.stopPropagation(); toggleHalfAndHalf(product, cat) }}
                              disabled={dimmed || isChica}
                              aria-pressed={isPendingHalf}
                              title={isChica ? 'Mitad y mitad sólo está disponible en tamaño grande' : undefined}
                              aria-label={
                                isChica
                                  ? `Mitad y mitad no disponible en tamaño chica para ${product.name}`
                                  : isPendingHalf
                                    ? `Cancelar mitad y mitad con ${product.name}`
                                    : halfPending
                                      ? `Completar mitad y mitad con ${product.name}`
                                      : `Pedir ${product.name} mitad y mitad`
                              }
                            >
                              ½
                            </button>
                          )}
                          {/* Sin contador: con tres variantes posibles por producto
                              (entero, chico, combinado) un badge en el catálogo no puede
                              representarlas sin confundir. Lo agregado se lee en el panel
                              "Productos seleccionados", que ya las lista por separado. */}
                          <button
                            type="button"
                            className="nom-add-btn"
                            onClick={e => { e.stopPropagation(); addToCart(product) }}
                            disabled={dimmed}
                            aria-label={`Agregar ${product.name}`}
                          >
                            +
                          </button>
                        </div>
                      )
                    })}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Divider */}
          <div className="nom-divider" aria-hidden="true" />

          {/* Right: items + form + footer */}
          <div className="nom-col-right">

            {/* Items */}
            <div className="nom-items-wrap">
            {/* Mismo comportamiento que la flecha de abajo, invertido: aparece sólo si quedó
                contenido tapado arriba y sube de a un ítem. */}
            {hasMoreItemsAbove && (
              <div className="nom-scroll-hint nom-scroll-hint--top">
                <button
                  type="button"
                  className="nom-scroll-hint-btn nom-scroll-hint-btn--up"
                  onClick={() => scrollItemsBy(-1)}
                  aria-label="Ver el producto anterior"
                >
                  <ChevronDownIcon />
                </button>
              </div>
            )}
            <div className="nom-items-section" ref={itemsSectionRef}>
              <div className="nom-section-label">PRODUCTOS SELECCIONADOS</div>
              {cartItems.length === 0 ? (
                <div className="nom-cart-empty">Ningún producto seleccionado</div>
              ) : (
                <div className="nom-cart-list">
                  {cartItems.map(item => (
                    <div key={item.key} className="nom-cart-item">
                      <div className="nom-cart-qty-ctrl">
                        <button
                          type="button"
                          className="nom-qty-btn"
                          onClick={() => decreaseQuantity(item.key)}
                          aria-label="Disminuir cantidad"
                        >
                          −
                        </button>
                        <span className="nom-qty-value">{item.quantity}</span>
                        <button
                          type="button"
                          className="nom-qty-btn"
                          onClick={() => increaseQuantity(item.key)}
                          aria-label="Aumentar cantidad"
                        >
                          +
                        </button>
                      </div>
                      <span className="nom-cart-name">{orderItemDisplayName(item)}</span>
                      <span className="nom-cart-subtotal">
                        {formatPrice(item.unitPrice * item.quantity)}
                      </span>
                      <button
                        type="button"
                        className="nom-cart-trash"
                        onClick={() => removeFromCart(item.key)}
                        aria-label={`Eliminar ${orderItemDisplayName(item)}`}
                      >
                        <TrashIcon />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
            {/* Sólo mientras haya contenido oculto abajo. El degradado no captura clicks;
                la flecha sí, y baja de a un ítem. */}
            {hasMoreItemsBelow && (
              <div className="nom-scroll-hint nom-scroll-hint--bottom">
                <button
                  type="button"
                  className="nom-scroll-hint-btn"
                  onClick={() => scrollItemsBy(1)}
                  aria-label="Ver el siguiente producto"
                >
                  <ChevronDownIcon />
                </button>
              </div>
            )}
            </div>

            {/* Form */}
            <div className="nom-form-section">

              {import.meta.env.DEV && (
                <button
                  type="button"
                  className="nom-debug-fill-btn"
                  onClick={handleDebugFill}
                  title="Rellenar campos con datos de prueba"
                >
                  🛠 Fill Debug Data
                </button>
              )}

              <div className="nom-field">
                <div className="nom-field-label">MODALIDAD</div>
                <div className="nom-toggle-group">
                  <button
                    type="button"
                    className={`nom-toggle-btn${orderType === 'DELIVERY' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setOrderType('DELIVERY')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M3 11l1-4h8l2 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      <circle cx="5.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
                      <circle cx="18.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
                      <path d="M14 7h4l2 4H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    DELIVERY
                  </button>
                  <button
                    type="button"
                    className={`nom-toggle-btn${orderType === 'TAKEAWAY' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setOrderType('TAKEAWAY')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      <polyline points="9 22 9 12 15 12 15 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    TAKEAWAY
                  </button>
                </div>
              </div>

              {orderType === 'DELIVERY' && (
                <div className="nom-field">
                  <div className="nom-field-label">DIRECCIÓN</div>
                  <input
                    className="nom-text-input"
                    type="text"
                    placeholder="Dirección de entrega..."
                    value={deliveryAddress}
                    onChange={e => setDeliveryAddress(e.target.value)}
                  />
                </div>
              )}

              <div className="nom-field">
                <div className="nom-field-label">MEDIO DE PAGO</div>
                <div className="nom-toggle-group">
                  <button
                    type="button"
                    className={`nom-toggle-btn${paymentMethod === 'CASH' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setPaymentMethod('CASH')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect x="2" y="6" width="20" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
                      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
                    </svg>
                    EFECTIVO
                  </button>
                  <button
                    type="button"
                    className={`nom-toggle-btn${paymentMethod === 'QR_CODE' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setPaymentMethod('QR_CODE')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect x="3" y="3" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <rect x="14" y="3" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <rect x="3" y="14" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <path d="M14 14h2v2h-2zM18 14h2M18 18h2v2h-2zM14 18h2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                    </svg>
                    QR
                  </button>
                </div>
              </div>

              <div className="nom-field-row">
                <div className="nom-field">
                  <div className="nom-field-label">NOMBRE</div>
                  <input
                    className="nom-text-input"
                    type="text"
                    placeholder="Nombre del cliente..."
                    value={customerName}
                    onChange={e => setCustomerName(e.target.value)}
                  />
                </div>
                <div className="nom-field">
                  <div className="nom-field-label">TELÉFONO</div>
                  <input
                    className="nom-text-input"
                    type="tel"
                    placeholder="Teléfono..."
                    value={customerPhone}
                    onChange={e => setCustomerPhone(e.target.value)}
                  />
                </div>
              </div>

              <div className="nom-field">
                <div className="nom-field-label">NOTAS</div>
                <textarea
                  className="nom-textarea"
                  placeholder="Notas opcionales para el pedido"
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                  rows={2}
                />
              </div>

            </div>

            {/* Footer */}
            <div className="nom-footer">
              <div className="nom-totals">
                <div className="nom-total-row">
                  <span className="nom-total-label">Subtotal</span>
                  <span className="nom-total-value">{formatPrice(subtotal)}</span>
                </div>
                {orderType === 'DELIVERY' && computedDeliveryFee > 0 && (
                  <div className="nom-total-row">
                    <span className="nom-total-label">Envío</span>
                    <span className="nom-total-value">{formatPrice(computedDeliveryFee)}</span>
                  </div>
                )}
                {branchServiceFee > 0 && (
                  <div className="nom-total-row">
                    <span className="nom-total-label">Servicio</span>
                    <span className="nom-total-value">{formatPrice(branchServiceFee)}</span>
                  </div>
                )}
                <div className="nom-total-row nom-total-row--grand">
                  <span className="nom-total-grand-label">TOTAL</span>
                  <span className="nom-total-grand-value">{formatPrice(total)}</span>
                </div>
              </div>

              <div className="nom-footer-right">
                {error && <div className="nom-error">{error}</div>}
                <button
                  type="button"
                  className="nom-confirm-btn"
                  onClick={handleConfirm}
                  disabled={!canConfirm || submitting}
                >
                  {submitting ? '···' : 'Confirmar pedido →'}
                </button>
              </div>
            </div>

          </div>
        </div>
      </div>

      {/* US-15-F-09: confirmación explícita de productos no disponibles */}
      {confirmUnavailable && (
        <div className="nom-confirm-overlay" onClick={e => { e.stopPropagation(); setConfirmUnavailable(false) }}>
          <div
            className="nom-confirm-dialog"
            onClick={e => e.stopPropagation()}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="nom-confirm-title"
          >
            <h3 className="nom-confirm-title" id="nom-confirm-title">Productos no disponibles</h3>
            <p className="nom-confirm-text">
              Este pedido incluye productos marcados como no disponibles:
            </p>
            <ul className="nom-confirm-list">
              {unavailableItems.map(i => (
                <li key={i.key}>{orderItemDisplayName(i)}</li>
              ))}
            </ul>
            <p className="nom-confirm-text">¿Confirmar de todas formas?</p>
            <div className="nom-confirm-actions">
              <button
                type="button"
                className="nom-confirm-cancel-btn"
                onClick={() => setConfirmUnavailable(false)}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="nom-confirm-force-btn"
                onClick={submitOrder}
                disabled={submitting}
              >
                Confirmar de todas formas
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
