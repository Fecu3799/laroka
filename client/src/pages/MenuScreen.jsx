import { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { motion as Motion, AnimatePresence } from 'framer-motion'
import { LaRokaLogo } from '../components/LaRokaLogo'
import { BottomNav } from '../components/BottomNav'
import { ProductDetailScreen } from './ProductDetailScreen'
import { CartScreen } from './CartScreen'
import { OrderTrackingBanner } from '../features/order/OrderTrackingBanner'
import { WelcomeModal } from '../components/WelcomeModal'
import { useCart } from '../hooks/useCart'
import { getTenantProfile } from '../services/tenantService'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const INTRO_SEEN_KEY = 'laroka_intro_seen'

function formatPrice(price) {
  return `$${Number(price).toLocaleString('es-AR')}`
}

function SearchIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="menu-search-icon">
      <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="2.2"/>
      <path d="M21 21l-4.35-4.35" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/>
    </svg>
  )
}

function InfoIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
      <path d="M12 11v5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <circle cx="12" cy="7.6" r="1.2" fill="currentColor" />
    </svg>
  )
}

function AddIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round"/>
    </svg>
  )
}

function ProductImage({ src, alt }) {
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)

  if (!src || error) {
    return <div className="product-image product-image--placeholder" aria-hidden="true" />
  }

  return (
    <div className="product-image-wrapper">
      {!loaded && <div className="product-image-skeleton" aria-hidden="true" />}
      <img
        src={src}
        alt={alt}
        className={`product-image${loaded ? ' product-image--loaded' : ''}`}
        onLoad={() => setLoaded(true)}
        onError={() => setError(true)}
      />
    </div>
  )
}

function ProductCard({ product, onSelect, onAdd }) {
  return (
    <li
      className="product-card"
      onClick={() => onSelect(product)}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onSelect(product)}
    >
      <ProductImage src={product.imageUrl} alt={product.name} />
      <div className="product-info">
        <h3 className="product-name">{product.name}</h3>
        {product.description && (
          <p className="product-description">{product.description}</p>
        )}
        <span className="product-price">{formatPrice(product.price)}</span>
      </div>
      <button
        className="product-add-btn"
        aria-label={`Agregar ${product.name}`}
        onClick={e => { e.stopPropagation(); onAdd(product) }}
      >
        <AddIcon />
      </button>
    </li>
  )
}

function ComingSoon() {
  return (
    <div className="menu-coming-soon">
      <p className="menu-coming-soon-text">Próximamente</p>
    </div>
  )
}

function BranchSwitchWarningModal({ onConfirm, onCancel }) {
  return (
    <div
      className="branch-switch-overlay"
      onClick={(e) => { if (e.target === e.currentTarget) onCancel() }}
    >
      <div className="branch-switch-modal">
        <p className="branch-switch-title">¿Cambiar sucursal?</p>
        <p className="branch-switch-body">
          Tenés productos de otra sucursal en tu carrito. Si cambiás, se vaciará el carrito.
        </p>
        <div className="branch-switch-actions">
          <button className="branch-switch-cancel" onClick={onCancel}>
            Cancelar
          </button>
          <button className="branch-switch-confirm" onClick={onConfirm}>
            Cambiar sucursal
          </button>
        </div>
      </div>
    </div>
  )
}

export function MenuScreen({ branchId, branchName, onChangeBranch, paymentFailureRecovery = null, onPaymentFailureConsumed = () => {}, pendingPaymentRecovery = null, onPendingPaymentConsumed = () => {} }) {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [activeCategory, setActiveCategory] = useState(null)
  const [activeCategoryIndex, setActiveCategoryIndex] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState(() => (paymentFailureRecovery || pendingPaymentRecovery) ? 'cart' : 'menu')
  const [selectedProduct, setSelectedProduct] = useState(null)
  const [cartFailureData, setCartFailureData] = useState(() =>
    paymentFailureRecovery
      ? { orderId: paymentFailureRecovery.orderId, formData: paymentFailureRecovery.formData }
      : null
  )
  const [cartPendingData, setCartPendingData] = useState(() =>
    pendingPaymentRecovery ? { orderId: pendingPaymentRecovery.orderId } : null
  )
  const [showSwitchWarning, setShowSwitchWarning] = useState(false)
  const [tenantProfile, setTenantProfile] = useState(null)
  const [showWelcome, setShowWelcome] = useState(false)
  const [branch, setBranch] = useState(null)

  const retryRef = useRef(null)
  const swipeContainerRef = useRef(null)
  const trackRef = useRef(null)
  const activeCategoryIndexRef = useRef(0)
  const gestureRef = useRef({ active: false, startX: 0, startY: 0, direction: null })

  const { items, addItem, removeItem, updateQty, clearCart, count } = useCart(
    (paymentFailureRecovery?.items ?? pendingPaymentRecovery?.items)?.map(i => ({ ...i })) || []
  )

  // Keep ref in sync after every render (same pattern as OrderTrackingBanner)
  useEffect(() => {
    activeCategoryIndexRef.current = activeCategoryIndex
  })

  useEffect(() => {
    if (!paymentFailureRecovery) return
    onPaymentFailureConsumed()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paymentFailureRecovery])

  useEffect(() => {
    if (!pendingPaymentRecovery) return
    onPendingPaymentConsumed()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingPaymentRecovery])

  const handleCartFailureConsumed = useCallback(() => {
    setCartFailureData(null)
  }, [])

  const handleSelectProduct = useCallback((product) => {
    const cat = categories.find(c => c.products.some(p => p.id === product.id))
    setSelectedProduct({ ...product, categoryName: cat?.categoryName || '' })
  }, [categories])

  const handleCloseDetail = useCallback(() => {
    setSelectedProduct(null)
  }, [])

  const handleAddToCart = useCallback((product, qty) => {
    addItem(product, qty)
  }, [addItem])

  // Perfil del negocio (US-13-F-02): se carga al montar la pantalla del menú.
  // Si no hay perfil (404), no se muestra el modal ni el ícono del header.
  // El modal de bienvenida aparece sobre el menú la primera vez (mientras no
  // exista la key laroka_intro_seen en localStorage).
  useEffect(() => {
    let cancelled = false
    getTenantProfile()
      .then(profile => {
        if (cancelled || !profile) return
        setTenantProfile(profile)
        if (localStorage.getItem(INTRO_SEEN_KEY) == null) {
          setShowWelcome(true)
        }
      })
      .catch(() => { /* error de red: no bloquea el menú */ })
    return () => { cancelled = true }
  }, [])

  const handleCloseWelcome = useCallback(() => {
    localStorage.setItem(INTRO_SEEN_KEY, 'true')
    setShowWelcome(false)
  }, [])

  const handleOpenWelcome = useCallback(() => {
    setShowWelcome(true)
  }, [])

  // Datos públicos de la sucursal (incluye address y schedule, US-13-F-05).
  // Alimenta la sección de dirección/horario del modal de presentación.
  useEffect(() => {
    let cancelled = false
    fetch(`${API_BASE}/branches/${branchId}`)
      .then(res => (res.ok ? res.json() : null))
      .then(data => { if (!cancelled) setBranch(data) })
      .catch(() => { /* best-effort: no bloquea el menú */ })
    return () => { cancelled = true }
  }, [branchId])

  useEffect(() => {
    let cancelled = false
    const run = async () => {
      setLoading(true)
      setError(null)
      try {
        const res = await fetch(`${API_BASE}/branches/${branchId}/menu`)
        if (!res.ok) throw new Error(`Error ${res.status}`)
        const data = await res.json()
        if (!cancelled) {
          setCategories(data)
          if (data.length > 0) {
            setActiveCategory(data[0].categoryId)
            setActiveCategoryIndex(0)
          }
        }
      } catch (err) {
        if (!cancelled) setError(err.message || 'No se pudo cargar el menú')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    retryRef.current = run
    run()
    return () => { cancelled = true }
  }, [branchId])

  const currentProducts = useMemo(() => {
    const cat = categories.find(c => c.categoryId === activeCategory)
    if (!cat) return []
    const q = searchQuery.trim().toLowerCase()
    if (!q) return cat.products
    return cat.products.filter(p => p.name.toLowerCase().includes(q))
  }, [categories, activeCategory, searchQuery])

  const drinks = useMemo(
    () => categories.find(c => c.categoryId === 3)?.products ?? [],
    [categories]
  )

  const handleCategoryChange = (categoryId) => {
    const newIndex = categories.findIndex(c => c.categoryId === categoryId)
    if (newIndex !== -1 && newIndex !== activeCategoryIndex) {
      setActiveCategoryIndex(newIndex)
      setActiveCategory(categoryId)
      if (trackRef.current && categories.length > 0) {
        trackRef.current.style.transition = 'transform 0.3s ease-out'
        trackRef.current.style.transform = `translateX(${-(newIndex / categories.length) * 100}%)`
      }
    }
  }

  const isMenuTab = activeTab === 'menu'
  const isProfileTab = activeTab === 'profile'

  const handleChangeBranchClick = useCallback(() => {
    if (count > 0) setShowSwitchWarning(true)
    else onChangeBranch()
  }, [count, onChangeBranch])

  const handleConfirmBranchSwitch = useCallback(() => {
    clearCart()
    onChangeBranch()
  }, [clearCart, onChangeBranch])

  const handleCancelBranchSwitch = useCallback(() => {
    setShowSwitchWarning(false)
  }, [])

  function handleGestureDown(e) {
    if (e.pointerType === 'mouse') return
    gestureRef.current = { active: true, startX: e.clientX, startY: e.clientY, direction: null }
  }

  function handleGestureMove(e) {
    const g = gestureRef.current
    if (!g.active) return
    const dx = e.clientX - g.startX
    const dy = e.clientY - g.startY
    if (g.direction === null) {
      if (Math.abs(dx) < 5 && Math.abs(dy) < 5) return
      g.direction = Math.abs(dy) > Math.abs(dx) ? 'vertical' : 'horizontal'
      if (g.direction === 'vertical') { g.active = false; return }
      e.currentTarget.setPointerCapture(e.pointerId)
    }
    if (g.direction !== 'horizontal') return
    const track = trackRef.current
    const outer = swipeContainerRef.current
    if (!track || !outer) return
    const n = categories.length
    if (n === 0) return
    const idx = activeCategoryIndexRef.current
    const blocked = (dx < 0 && idx >= n - 1) || (dx > 0 && idx <= 0)
    const clamped = blocked ? 0 : dx
    const outerWidth = outer.offsetWidth
    const totalOffsetPx = -idx * outerWidth + clamped
    const totalPercent = (totalOffsetPx / (n * outerWidth)) * 100
    track.style.transition = 'none'
    track.style.transform = `translateX(${totalPercent}%)`
  }

  function handleGestureUp(e) {
    const g = gestureRef.current
    if (!g.active || g.direction !== 'horizontal') { g.active = false; return }
    g.active = false
    const track = trackRef.current
    if (!track) return
    const dx = e.clientX - g.startX
    const n = categories.length
    const idx = activeCategoryIndexRef.current
    let newIndex = idx
    if (dx < 0 && idx < n - 1) newIndex = idx + 1
    else if (dx > 0 && idx > 0) newIndex = idx - 1
    const targetPercent = -(newIndex / n) * 100
    track.style.transition = 'transform 0.3s ease-out'
    track.style.transform = `translateX(${targetPercent}%)`
    if (newIndex !== idx) {
      setActiveCategoryIndex(newIndex)
      setActiveCategory(categories[newIndex].categoryId)
    }
  }

  function handleGestureCancel() {
    const g = gestureRef.current
    if (!g.active) return
    g.active = false
    const track = trackRef.current
    if (!track) return
    const n = categories.length
    const idx = activeCategoryIndexRef.current
    track.style.transition = 'transform 0.3s ease-out'
    track.style.transform = `translateX(${-(idx / n) * 100}%)`
  }

  return (
    <div className="menu-screen">
      <div className="menu-header-area">
        <header className="menu-header">
          <LaRokaLogo className="menu-logo" />
          <div className="menu-branch-info">
            <button
              className="menu-change-branch-btn"
              type="button"
              onClick={handleChangeBranchClick}
            >
              Cambiar sucursal
            </button>
            <div className="menu-branch-name-row">
              <span className="menu-branch-name">{branchName || '—'}</span>
              {tenantProfile && (
                <button
                  className="menu-about-btn"
                  type="button"
                  onClick={handleOpenWelcome}
                  aria-label="Sobre nosotros"
                >
                  <InfoIcon />
                </button>
              )}
            </div>
          </div>
        </header>
      </div>

      <main className={`menu-main${activeTab === 'cart' ? ' menu-main--cart' : ''}`}>
        <div style={isMenuTab ? undefined : { display: 'none' }}>
          <OrderTrackingBanner branchId={branchId} />
        </div>

        {activeTab === 'cart' ? (
          <CartScreen
            items={items}
            extras={drinks}
            onBack={() => setActiveTab('menu')}
            onRemove={removeItem}
            onUpdateQty={updateQty}
            onClear={clearCart}
            onAddExtra={addItem}
            paymentFailure={cartFailureData}
            onPaymentFailureConsumed={handleCartFailureConsumed}
            pendingPayment={cartPendingData}
            onPendingPaymentConsumed={() => setCartPendingData(null)}
          />
        ) : isProfileTab ? (
          <ComingSoon />
        ) : (
          <>
            <div className="menu-search-wrapper">
              <label className="menu-search">
                <SearchIcon />
                <input
                  type="search"
                  className="menu-search-input"
                  placeholder="¿Qué producto deseas buscar?"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  aria-label="Buscar productos"
                />
              </label>
            </div>

            <nav className="menu-tabs" aria-label="Categorías">
              {categories.map(cat => (
                <button
                  key={cat.categoryId}
                  className={`menu-tab${activeCategory === cat.categoryId ? ' menu-tab--active' : ''}`}
                  onClick={() => handleCategoryChange(cat.categoryId)}
                >
                  {cat.categoryName}
                </button>
              ))}
            </nav>

            {loading ? (
              <div className="menu-loading" role="status" aria-label="Cargando menú">
                <div className="branch-spinner" />
              </div>
            ) : error ? (
              <div className="menu-error" role="alert">
                <p>{error}</p>
                <button
                  className="branch-retry-btn"
                  onClick={() => retryRef.current?.()}
                >
                  Reintentar
                </button>
              </div>
            ) : searchQuery.trim() ? (
              currentProducts.length === 0 ? (
                <div className="menu-empty">
                  <p>No se encontraron productos</p>
                </div>
              ) : (
                <ul className="menu-list" role="list">
                  {currentProducts.map(product => (
                    <ProductCard
                      key={product.id}
                      product={product}
                      onSelect={handleSelectProduct}
                      onAdd={p => addItem(p, 1)}
                    />
                  ))}
                </ul>
              )
            ) : (
              <div
                ref={swipeContainerRef}
                className="menu-swipe-outer"
                onPointerDown={handleGestureDown}
                onPointerMove={handleGestureMove}
                onPointerUp={handleGestureUp}
                onPointerCancel={handleGestureCancel}
              >
                <div
                  ref={trackRef}
                  className="menu-swipe-track"
                  style={{
                    width: `${categories.length * 100}%`,
                    transform: `translateX(${categories.length > 0 ? -(activeCategoryIndex / categories.length) * 100 : 0}%)`,
                  }}
                >
                  {categories.map(cat => (
                    <div
                      key={cat.categoryId}
                      className="menu-swipe-slide"
                      style={{ width: `${100 / categories.length}%` }}
                    >
                      {cat.products.length === 0 ? (
                        <div className="menu-empty">
                          <p>No se encontraron productos</p>
                        </div>
                      ) : (
                        <ul className="menu-list" role="list">
                          {cat.products.map(product => (
                            <ProductCard
                              key={product.id}
                              product={product}
                              onSelect={handleSelectProduct}
                              onAdd={p => addItem(p, 1)}
                            />
                          ))}
                        </ul>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </main>

      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} cartCount={count} />

      <AnimatePresence>
        {selectedProduct && (
          <Motion.div
            key="product-detail"
            className="detail-screen-wrapper"
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'tween', duration: 0.28, ease: [0.32, 0.72, 0, 1] }}
          >
            <ProductDetailScreen
              product={selectedProduct}
              onBack={handleCloseDetail}
              onAddToCart={handleAddToCart}
            />
          </Motion.div>
        )}
      </AnimatePresence>

      {showSwitchWarning && (
        <BranchSwitchWarningModal
          onConfirm={handleConfirmBranchSwitch}
          onCancel={handleCancelBranchSwitch}
        />
      )}

      {showWelcome && tenantProfile && (
        <WelcomeModal profile={tenantProfile} branch={branch} onClose={handleCloseWelcome} />
      )}
    </div>
  )
}
