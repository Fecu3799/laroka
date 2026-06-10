import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import useBranch from '../hooks/useBranch'
import { fetchBranches } from '../services/branchService'
import './Login.css'
import './BranchSelect.css'

export default function BranchSelect() {
  const navigate = useNavigate()
  const { token, role } = useAuth()
  const { activeBranchId, setActiveBranch } = useBranch()
  const [branches, setBranches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (role && role !== 'ADMIN') {
      navigate('/orders', { replace: true })
    }
  }, [role, navigate])

  useEffect(() => {
    if (activeBranchId != null) {
      navigate('/orders', { replace: true })
    }
  }, [activeBranchId, navigate])

  useEffect(() => {
    if (!token) return
    async function load() {
      try {
        const data = await fetchBranches(token)
        setBranches(data)
      } catch {
        setError('No se pudieron cargar las sucursales.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [token])

  function handleSelect(branch) {
    setActiveBranch(branch.id, branch.name)
    navigate('/orders', { replace: true })
  }

  return (
    <div className="login-page">
      <span className="login-version-badge" aria-label="Versión del panel">v2.0</span>
      <div className="login-content">
        <div className="login-card" role="main">
          <div className="login-icon-wrap" aria-hidden="true">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="var(--color-accent)" strokeWidth="1.8" />
              <polyline points="9 22 9 12 15 12 15 22" stroke="var(--color-accent)" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </div>

          <h1 className="login-title">SELECCIONÁ SUCURSAL</h1>
          <p className="login-subtitle">¿En qué sucursal vas a operar hoy?</p>

          {loading && <p className="branch-select-loading">Cargando sucursales...</p>}

          {error && (
            <div className="login-error" role="alert">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
                <path d="M15 9l-6 6M9 9l6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
              {error}
            </div>
          )}

          {!loading && !error && (
            <div className="branch-select-list">
              {branches.map(branch => (
                <button
                  key={branch.id}
                  className="branch-select-btn"
                  onClick={() => handleSelect(branch)}
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" strokeWidth="1.8" />
                    <polyline points="9 22 9 12 15 12 15 22" stroke="currentColor" strokeWidth="1.8" />
                  </svg>
                  {branch.name}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
