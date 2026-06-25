import { useState, useEffect } from 'react'
import './CustomSelect.css'

/**
 * Dropdown custom que reemplaza al <select> nativo: las <option> no heredan
 * los estilos del panel (el SO las pinta con su propio tema). Render con div/lista
 * siguiendo la paleta de index.css — fondo oscuro, texto claro, hover con acento.
 */
export default function CustomSelect({
  id,
  value,
  onChange,
  options,
  placeholder = 'Seleccionar…',
  ariaLabelledBy,
  disabled = false,
}) {
  const [open, setOpen] = useState(false)

  const selected = options.find(o => String(o.value) === String(value)) ?? null

  useEffect(() => {
    if (!open) return
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  function pick(val) {
    onChange(val)
    setOpen(false)
  }

  return (
    <div className="cs-root">
      <button
        type="button"
        id={id}
        className={`cs-trigger${open ? ' cs-trigger--open' : ''}`}
        onClick={() => !disabled && setOpen(o => !o)}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={ariaLabelledBy}
      >
        <span className={`cs-value${selected ? '' : ' cs-value--placeholder'}`}>
          {selected ? selected.label : placeholder}
        </span>
        <svg className="cs-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open && (
        <>
          {/* Cierra al hacer click fuera (queda dentro del panel, no propaga al backdrop del drawer). */}
          <div className="cs-backdrop" onClick={() => setOpen(false)} aria-hidden="true" />
          <ul className="cs-list" role="listbox" aria-labelledby={ariaLabelledBy}>
            {options.length === 0 ? (
              <li className="cs-empty">Sin opciones</li>
            ) : (
              options.map(o => {
                const isSel = String(o.value) === String(value)
                return (
                  <li key={o.value} role="option" aria-selected={isSel}>
                    <button
                      type="button"
                      className={`cs-option${isSel ? ' cs-option--selected' : ''}`}
                      onClick={() => pick(o.value)}
                    >
                      <span className="cs-option-label">{o.label}</span>
                      {isSel && (
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                          <path d="m5 12 5 5 9-10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                      )}
                    </button>
                  </li>
                )
              })
            )}
          </ul>
        </>
      )}
    </div>
  )
}
