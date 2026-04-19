import { useState, useEffect, useRef, useCallback } from 'react'
import { LaRokaLogo } from './LaRokaLogo'
import heroPizzaImg from '../assets/hero-pizza.jpg'
import playaImg from '../assets/cities/playa.png'
import madrynImg from '../assets/cities/madryn.png'
import rawsonImg from '../assets/cities/rawson.jpg'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

const FALLBACK_COLORS = ['#0F954B', '#095E2F', '#65A369']

function getCityImage(name) {
  const n = name.toLowerCase()
  if (n.includes('playa')) return { src: playaImg, style: { transform: 'scale(1.5)', transformOrigin: 'center' } }
  if (n.includes('madryn')) return { src: madrynImg, style: null }
  if (n.includes('rawson')) return { src: rawsonImg, style: null }
  return null
}

function CityThumb({ index, name }) {
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
      <div
        className="branch-city-circle"
        style={{ background: FALLBACK_COLORS[index % FALLBACK_COLORS.length] }}
      >
        <span className="branch-city-initial" aria-hidden="true">{name[0]}</span>
      </div>
    </div>
  )
}

export function BranchSelection({ onSelect }) {
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const retryRef = useRef(null)

  const retry = useCallback(() => retryRef.current?.(), [])

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const res = await fetch(`${API_BASE}/branches`)
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
                  className="branch-btn"
                  onClick={() => onSelect(branch.id)}
                  aria-label={`Seleccionar sucursal ${branch.name}`}
                >
                  <CityThumb index={index} name={branch.name} />
                  <span className="branch-btn-name">{branch.name}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
