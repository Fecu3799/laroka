import { useState } from 'react'

const STORAGE_KEY = 'pedisur_backoffice_theme'

// El tema vigente lo fija el <script> inline de index.html antes del render, así que
// se lee del atributo data-theme del <html> (fuente de verdad sincrónica).
function currentTheme() {
  return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark'
}

function SunIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="4" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"
        stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z"
        stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

// Toggle de tema oscuro/claro (US-15-F-04). Visible para todos los roles.
export default function ThemeToggle() {
  const [theme, setTheme] = useState(currentTheme)

  function toggle() {
    const next = theme === 'light' ? 'dark' : 'light'
    document.documentElement.setAttribute('data-theme', next)
    try { localStorage.setItem(STORAGE_KEY, next) } catch { /* localStorage no disponible */ }
    setTheme(next)
  }

  const target = theme === 'light' ? 'oscuro' : 'claro'
  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggle}
      aria-label={`Cambiar a tema ${target}`}
      title={`Cambiar a tema ${target}`}
    >
      {theme === 'light' ? <MoonIcon /> : <SunIcon />}
    </button>
  )
}
