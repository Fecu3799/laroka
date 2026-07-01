import { useState, useEffect, useRef, useCallback } from 'react'
import { LaRokaLogo } from '../components/LaRokaLogo'
import heroPizzaImg from '../assets/hero-pizza.webp'
import playaImg from '../assets/cities/playa.webp'
import madrynImg from '../assets/cities/madryn.webp'
import rawsonImg from '../assets/cities/rawson.webp'
import { TENANT_ID } from '../config'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

function getCityImage(name) {
  const n = name.toLowerCase()
  if (n.includes('playa')) return { src: playaImg, style: { transform: 'scale(1.5)', transformOrigin: 'center' } }
  if (n.includes('madryn')) return { src: madrynImg, style: null }
  if (n.includes('rawson')) return { src: rawsonImg, style: null }
  return null
}

function CityThumb({ index, name, imageUrl }) {
  const colorVars = ['--bg-card', '--bg-secondary', '--border']
  const colorVar = colorVars[index % colorVars.length]

  // US-15-CF-04: si la sucursal cargó su propia imagen, se usa directamente.
  if (imageUrl) {
    return (
      <div className="branch-city-thumb">
        <img src={imageUrl} alt="" className="branch-city-img" aria-hidden="true" />
      </div>
    )
  }

  // Fallback (sin imagen propia): asset genérico por ciudad mapeado por nombre.
  const result = getCityImage(name)
  if (result) {
    return (
      <div className="branch-city-thumb">
        <img src={result.src} alt="" className="branch-city-img" style={result.style} aria-hidden="true" />
      </div>
    )
  }
  return (
    <div className="branch-city-thumb">
      <div className="branch-city-circle" style={{ background: `var(${colorVar})` }}>
        <span className="branch-city-initial" aria-hidden="true">{name[0]}</span>
      </div>
    </div>
  )
}

async function prefetchMenuImages(branchId) {
  try {
    const res = await fetch(`${API_BASE}/branches/${branchId}/menu`)
    if (!res.ok) return
    const categories = await res.json()
    categories.forEach(cat => {
      cat.products?.forEach(product => {
        if (product.imageUrl) {
          const img = new Image()
          img.src = product.imageUrl
        }
      })
    })
  } catch {
    // best-effort
  }
}

const SLIDE_DURATION = 450

export function BranchSelection({ onSelect }) {
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [slidingId, setSlidingId] = useState(null)
  const retryRef = useRef(null)
  const slideTimerRef = useRef(null)
  const dragRef = useRef({ active: false, startX: 0, labelEl: null, maxPx: 0, hasDragged: false, branch: null })
  const dragSuppressClickRef = useRef(false)

  const retry = useCallback(() => retryRef.current?.(), [])

  const handleBranchClick = useCallback((branchId, branchName, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes) => {
    if (slidingId) return
    setSlidingId(branchId)
    prefetchMenuImages(branchId)
    slideTimerRef.current = setTimeout(() => {
      onSelect({ id: branchId, name: branchName, deliveryFee, serviceFee, phone, estimatedDeliveryMinutes })
    }, SLIDE_DURATION)
  }, [slidingId, onSelect])

  useEffect(() => () => clearTimeout(slideTimerRef.current), [])

  function handlePointerDown(e, branch) {
    if (slidingId) return
    const labelEl = e.currentTarget.querySelector('.branch-label')
    if (!labelEl) return
    dragRef.current = {
      active: true, startX: e.clientX, labelEl,
      maxPx: labelEl.offsetWidth * 0.5385,
      hasDragged: false, branch,
    }
    e.currentTarget.setPointerCapture(e.pointerId)
  }

  function handlePointerMove(e) {
    const d = dragRef.current
    if (!d.active) return
    const dx = e.clientX - d.startX
    if (dx < 8) return
    d.hasDragged = true
    d.labelEl.style.transition = 'none'
    d.labelEl.style.transform = `translateX(${Math.min(dx, d.maxPx)}px)`
  }

  function handlePointerUp() {
    const d = dragRef.current
    if (!d.active) return
    d.active = false
    if (!d.hasDragged) {
      d.labelEl.style.transition = ''
      d.labelEl.style.transform = ''
      return
    }
    dragSuppressClickRef.current = true
    d.labelEl.style.transition = 'transform 0.2s ease-out'
    d.labelEl.style.transform = `translateX(${d.maxPx}px)`
    setSlidingId(d.branch.id)
    prefetchMenuImages(d.branch.id)
    slideTimerRef.current = setTimeout(() => onSelect(d.branch), 200)
  }

  function handlePointerCancel() {
    const d = dragRef.current
    if (!d.active) return
    d.active = false
    d.labelEl.style.transition = 'transform 0.2s ease-out'
    d.labelEl.style.transform = 'translateX(0)'
    setTimeout(() => { d.labelEl.style.transition = ''; d.labelEl.style.transform = '' }, 200)
  }

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const res = await fetch(`${API_BASE}/branches?tenantId=${encodeURIComponent(TENANT_ID)}`)
        if (!res.ok) throw new Error(`Error ${res.status}`)
        const data = await res.json()
        if (!cancelled) setBranches(data)
      } catch (err) {
        if (!cancelled) setError(err.message || 'No se pudo conectar')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    retryRef.current = load
    load()
    return () => { cancelled = true }
  }, [])

  return (
    <div className="branch-selection">
      <div className="branch-hero" style={{ backgroundImage: `url(${heroPizzaImg})` }} aria-hidden="true">
        <div className="branch-hero-overlay" />
        <div className="branch-hero-logo">
          <LaRokaLogo className="hero-logo" />
        </div>
      </div>

      <div className="branch-panel">
        <p className="branch-panel-title">Seleccioná tu sucursal para ver el menú</p>

        {loading && (
          <div className="branch-loading" role="status" aria-label="Cargando sucursales">
            <div className="branch-spinner" />
          </div>
        )}

        {error && !loading && (
          <div className="branch-error" role="alert">
            <p>{error}</p>
            <button onClick={retry} className="branch-retry-btn">Reintentar</button>
          </div>
        )}

        {!loading && !error && (
          <ul className="branch-list" role="list">
            {branches.map((branch, index) => (
              <li key={branch.id}>
                <button
                  className={`branch-btn${slidingId === branch.id ? ' branch-btn--sliding' : ''}`}
                  onClick={() => {
                    if (dragSuppressClickRef.current) { dragSuppressClickRef.current = false; return }
                    handleBranchClick(branch.id, branch.name, branch.deliveryFee, branch.serviceFee, branch.phone, branch.estimatedDeliveryMinutes)
                  }}
                  onPointerDown={(e) => handlePointerDown(e, branch)}
                  onPointerMove={handlePointerMove}
                  onPointerUp={handlePointerUp}
                  onPointerCancel={handlePointerCancel}
                  aria-label={`Seleccionar sucursal ${branch.name}`}
                >
                  <CityThumb index={index} name={branch.name} imageUrl={branch.imageUrl} />
                  <div className="branch-label">
                    <span className="branch-btn-name">{branch.name}</span>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
