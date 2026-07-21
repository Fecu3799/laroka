import { useState, useEffect, useLayoutEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import './CustomSelect.css'

// Alto máximo de la lista: ~6 opciones. Más que eso no ayuda a elegir y empieza a tapar el
// formulario; el resto se alcanza scrolleando dentro del propio dropdown.
const MAX_LIST_HEIGHT = 220
// Aire mínimo contra el borde de la ventana para que la lista nunca quede pegada.
const VIEWPORT_MARGIN = 12

/**
 * Dropdown custom que reemplaza al <select> nativo: las <option> no heredan
 * los estilos del panel (el SO las pinta con su propio tema). Render con div/lista
 * siguiendo la paleta de index.css — fondo oscuro, texto claro, hover con acento.
 *
 * La lista se renderiza en un portal a <body> con posición fija, calculada desde el trigger.
 * Si se posiciona en absoluto dentro del formulario, los drawers que scrollean
 * (.sud-form tiene overflow-y: auto) la recortan: con muchas opciones se veía cortada contra
 * el borde del panel en vez de scrollear internamente. El portal la saca de ese contexto.
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
  const [position, setPosition] = useState(null)
  const triggerRef = useRef(null)

  const selected = options.find(o => String(o.value) === String(value)) ?? null

  useEffect(() => {
    if (!open) return
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  // Posición fija calculada desde el trigger. Se recalcula ante scroll (en captura, para
  // enterarse también del scroll de los contenedores internos) y resize, así la lista sigue
  // al trigger en vez de quedarse flotando donde se abrió.
  useLayoutEffect(() => {
    if (!open) {
      setPosition(null)
      return
    }
    function place() {
      const trigger = triggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const spaceBelow = window.innerHeight - rect.bottom - VIEWPORT_MARGIN
      const spaceAbove = rect.top - VIEWPORT_MARGIN
      // Se abre hacia arriba sólo si abajo no entra y arriba hay más lugar.
      const openUp = spaceBelow < MAX_LIST_HEIGHT && spaceAbove > spaceBelow
      setPosition({
        left: rect.left,
        width: rect.width,
        // El alto se acota al espacio real disponible: con la ventana chica la lista se
        // achica y scrollea, en vez de salirse de la pantalla.
        maxHeight: Math.min(MAX_LIST_HEIGHT, openUp ? spaceAbove : spaceBelow),
        ...(openUp
          ? { bottom: window.innerHeight - rect.top + 6 }
          : { top: rect.bottom + 6 }),
      })
    }
    place()
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [open])

  function pick(val) {
    onChange(val)
    setOpen(false)
  }

  const list = position && (
    <>
      {/* Cierra al hacer click fuera. Va en el portal junto con la lista para quedar por
          encima del drawer sin depender del stacking context del formulario. */}
      <div className="cs-backdrop" onClick={() => setOpen(false)} aria-hidden="true" />
      <ul
        className="cs-list"
        role="listbox"
        aria-labelledby={ariaLabelledBy}
        style={{
          left: position.left,
          width: position.width,
          maxHeight: position.maxHeight,
          ...(position.top != null ? { top: position.top } : { bottom: position.bottom }),
        }}
      >
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
  )

  return (
    <div className="cs-root">
      <button
        type="button"
        id={id}
        ref={triggerRef}
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

      {open && createPortal(list, document.body)}
    </div>
  )
}
