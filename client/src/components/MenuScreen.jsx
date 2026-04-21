import { useState, useEffect, useMemo, useRef } from 'react'
import { motion as Motion, AnimatePresence } from 'framer-motion'
import { LaRokaLogo } from './LaRokaLogo'
import { BottomNav } from './BottomNav'
import { BranchDropdown } from './BranchDropdown'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

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

function ChevronDown() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
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

function ProductCard({ product }) {
  return (
    <li className="product-card">
      <ProductImage src={product.imageUrl} alt={product.name} />
      <div className="product-info">
        <h3 className="product-name">{product.name}</h3>
        {product.description && (
          <p className="product-description">{product.description}</p>
        )}
        <span className="product-price">{formatPrice(product.price)}</span>
      </div>
      <button className="product-add-btn" aria-label={`Agregar ${product.name}`}>
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

export function MenuScreen({ branchId, branchName, onSwitchBranch }) {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [activeCategory, setActiveCategory] = useState(null)
  const [activeCategoryIndex, setActiveCategoryIndex] = useState(0)
  const [swipeDirection, setSwipeDirection] = useState('right')
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState('menu')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [branches, setBranches] = useState([])
  const retryRef = useRef(null)
  const prevCategoryIndexRef = useRef(0)

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
            prevCategoryIndexRef.current = 0
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

  const handleCategoryChange = (categoryId) => {
    const newIndex = categories.findIndex(c => c.categoryId === categoryId)
    if (newIndex !== activeCategoryIndex) {
      setSwipeDirection(newIndex > activeCategoryIndex ? 'left' : 'right')
      setActiveCategoryIndex(newIndex)
      setActiveCategory(categoryId)
      prevCategoryIndexRef.current = newIndex
    }
  }

  const isMenuTab = activeTab === 'menu'

  useEffect(() => {
    if (!drawerOpen) return
    let cancelled = false
    const load = async () => {
      try {
        const res = await fetch(`${API_BASE}/branches`)
        if (!res.ok) throw new Error('Error')
        const data = await res.json()
        if (!cancelled) setBranches(data)
      } catch {
        if (!cancelled) setBranches([])
      }
    }
    load()
    return () => { cancelled = true }
  }, [drawerOpen])

  const handleSelectBranchFromDrawer = (newBranchId) => {
    if (newBranchId !== branchId) {
      const branch = branches.find(b => b.id === newBranchId)
      if (branch) {
        onSwitchBranch(newBranchId, branch.name)
      }
    }
  }

  return (
    <div className="menu-screen">
      <div className="menu-header-area">
        <header className="menu-header">
          <LaRokaLogo className="menu-logo" />
          <div className="menu-branch-selector-wrapper">
            <button
              className={`menu-branch-selector${drawerOpen ? ' menu-branch-selector--open' : ''}`}
              onClick={(e) => {
                e.stopPropagation()
                setDrawerOpen(!drawerOpen)
              }}
              aria-label="Cambiar sucursal"
              aria-expanded={drawerOpen}
            >
              <span className="menu-branch-label">
                Sucursal <ChevronDown />
              </span>
              <span className="menu-branch-name">{branchName || '—'}</span>
            </button>
            <BranchDropdown
              isOpen={drawerOpen}
              branches={branches}
              currentBranchId={branchId}
              onClose={() => setDrawerOpen(false)}
              onSelectBranch={handleSelectBranchFromDrawer}
            />
          </div>
        </header>

        {isMenuTab && (
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
          </>
        )}
      </div>

      <main className="menu-main">
        {!isMenuTab ? (
          <ComingSoon />
        ) : loading ? (
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
        ) : currentProducts.length === 0 ? (
          <div className="menu-empty">
            <p>No se encontraron productos</p>
          </div>
        ) : (
          <AnimatePresence mode="wait">
            <Motion.ul
              key={activeCategory}
              className="menu-list"
              role="list"
              initial={{
                opacity: 0,
                x: swipeDirection === 'left' ? 80 : -80,
              }}
              animate={{
                opacity: 1,
                x: 0,
              }}
              exit={{
                opacity: 0,
                x: swipeDirection === 'left' ? -80 : 80,
              }}
              transition={{
                duration: 0.32,
                ease: [0.34, 1.56, 0.64, 1],
              }}
            >
              {currentProducts.map(product => (
                <ProductCard key={product.id} product={product} />
              ))}
            </Motion.ul>
          </AnimatePresence>
        )}
      </main>

      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  )
}
