import { useState, useEffect } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import logo from '../assets/logo.png'
import useAuth from '../hooks/useAuth'
import { logout } from '../services/authService'
import NewOrderModal from './NewOrderModal'
import './Layout.css'

const API_URL = import.meta.env.VITE_API_URL ?? ''

const NAV = [
  {
    action: 'new-order',
    label: 'NUEVA ORDEN',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/summary',
    label: 'RESUMEN',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="3" y="14" width="4" height="7" rx="1" fill="currentColor" opacity="0.6" />
        <rect x="10" y="9" width="4" height="12" rx="1" fill="currentColor" opacity="0.8" />
        <rect x="17" y="4" width="4" height="17" rx="1" fill="currentColor" />
      </svg>
    ),
  },
  {
    to: '/orders',
    end: true,
    label: 'PEDIDOS',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" stroke="currentColor" strokeWidth="1.8" />
        <rect x="9" y="3" width="6" height="4" rx="1" stroke="currentColor" strokeWidth="1.8" />
        <path d="M9 12h6M9 16h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/settings',
    label: 'CONFIG.',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" stroke="currentColor" strokeWidth="1.8" />
      </svg>
    ),
  },
]

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token, branchName, tenantName } = useAuth()
  const [time, setTime] = useState(new Date())
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const [newOrderCount, setNewOrderCount] = useState(0)
  const [newOrderModalOpen, setNewOrderModalOpen] = useState(false)

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!token) return
    let active = true
    let controller = null
    let reader = null

    async function connect() {
      while (active) {
        try {
          controller = new AbortController()
          const res = await fetch(`${API_URL}/backoffice/events`, {
            headers: { Authorization: `Bearer ${token}` },
            signal: controller.signal,
          })
          if (!res.ok) {
            if (res.status === 401 || res.status === 403) {
              active = false
              return
            }
            throw new Error(`HTTP ${res.status}`)
          }
          reader = res.body.getReader()
          const decoder = new TextDecoder()
          let buffer = ''
          while (active) {
            const { done, value } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop()
            for (const line of lines) {
              if (line.startsWith('data:')) {
                try {
                  const json = JSON.parse(line.slice(5).trim())
                  if (json.type === 'NEW_ORDER') setNewOrderCount(prev => prev + 1)
                } catch { /* noop */ }
              }
            }
          }
        } catch { /* noop */ }
        if (active) await new Promise(r => setTimeout(r, 2000))
      }
    }

    connect()
    return () => { active = false; controller?.abort(); reader?.cancel() }
  }, [token])

  useEffect(() => {
    async function check() {
      setConnectionStatus(prev => prev !== 'connected' ? 'reconnecting' : prev)
      try {
        const res = await fetch(`${API_URL}/actuator/health`)
        setConnectionStatus(res.ok ? 'connected' : 'disconnected')
      } catch {
        setConnectionStatus('disconnected')
      }
    }
    check()
    const id = setInterval(check, 30_000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (location.pathname === '/orders') setNewOrderCount(0)
  }, [location.pathname])

  const formattedTime = time.toLocaleTimeString('es-AR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  })

  return (
    <div className="layout">
      {/* ── Sidebar ──────────────────────────────────────────── */}
      <aside className="layout-sidebar">
        <div className="layout-sidebar-logo-box">
          <img src={logo} alt="LaRoka" className="layout-sidebar-logo-img" />
        </div>

        <nav className="layout-sidebar-nav" aria-label="Navegación principal">
          {NAV.map(({ to, label, icon, end, action }) =>
            action === 'new-order' ? (
              <button
                key="new-order"
                type="button"
                className="layout-nav-item layout-nav-btn"
                onClick={() => setNewOrderModalOpen(true)}
              >
                <span className="layout-nav-icon">{icon}</span>
                <span className="layout-nav-label">{label}</span>
              </button>
            ) : (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `layout-nav-item${isActive ? ' layout-nav-item--active' : ''}`
                }
              >
                <span className="layout-nav-icon">
                  {to === '/orders' ? (
                    <div style={{ position: 'relative' }}>
                      {icon}
                      {newOrderCount > 0 && location.pathname !== '/orders' && (
                        <span className="layout-nav-badge">{newOrderCount}</span>
                      )}
                    </div>
                  ) : icon}
                </span>
                <span className="layout-nav-label">{label}</span>
              </NavLink>
            )
          )}
        </nav>

        <div className="layout-sidebar-branch" aria-label="Sucursal activa">
          <div className="layout-branch-info">
            <span className="layout-branch-label">SUCURSAL</span>
            <span className="layout-branch-name">{branchName ?? '—'}</span>
          </div>
        </div>

        <button className="layout-logout-btn" onClick={handleLogout} aria-label="Cerrar sesión">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            <polyline points="16 17 21 12 16 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            <line x1="21" y1="12" x2="9" y2="12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span>SALIR</span>
        </button>
      </aside>

      {/* ── Right: header + content ───────────────────────────── */}
      <div className="layout-right">
        <header className="layout-header">
          <div className="layout-header-left">
            <span className="layout-header-icon" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <rect x="3" y="3" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
                <rect x="13" y="3" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
                <rect x="3" y="13" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
                <rect x="13" y="13" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
              </svg>
            </span>
            <span className="layout-header-title">PANEL DE CONTROL</span>
            <span className="layout-header-tenant">{tenantName ?? '—'}</span>
          </div>
          <div className="layout-header-right">
            <div className="layout-sse-status">
              <span
                className="layout-sse-dot"
                style={{
                  backgroundColor:
                    connectionStatus === 'connected'    ? '#22c55e' :
                    connectionStatus === 'reconnecting' ? '#f59e0b' : '#ef4444',
                }}
              />
              <span className="layout-sse-label">
                {connectionStatus === 'connected'    ? 'Conectado' :
                 connectionStatus === 'reconnecting' ? 'Reconectando...' : 'Sin conexión'}
              </span>
            </div>
            <span className="layout-header-theme">Verde oscuro</span>
            <span className="layout-header-clock" aria-live="polite">{formattedTime}</span>
          </div>
        </header>

        <main className="layout-main">
          <Outlet />
        </main>
      </div>

      <NewOrderModal
        open={newOrderModalOpen}
        onClose={() => setNewOrderModalOpen(false)}
      />
    </div>
  )
}
