import { useState, useEffect } from 'react'
import { Outlet, NavLink } from 'react-router-dom'
import logo from '../assets/logo.png'
import './Layout.css'

const NAV = [
  {
    to: '/orders/new',
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
  const [time, setTime] = useState(new Date())

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

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
          {NAV.map(({ to, label, icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `layout-nav-item${isActive ? ' layout-nav-item--active' : ''}`
              }
            >
              <span className="layout-nav-icon">{icon}</span>
              <span className="layout-nav-label">{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="layout-sidebar-branch" aria-label="Sucursal activa">
          <div className="layout-branch-info">
            <span className="layout-branch-label">SUCURSAL</span>
            <span className="layout-branch-name">Puerto Madryn</span>
          </div>
        </div>
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
            <span className="layout-header-pizzeria">La Roka Pizzeria</span>
          </div>
          <div className="layout-header-right">
            <span className="layout-header-theme">Verde oscuro</span>
            <span className="layout-header-clock" aria-live="polite">{formattedTime}</span>
          </div>
        </header>

        <main className="layout-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
