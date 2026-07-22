import { useEffect } from 'react'
import ReactDOM from 'react-dom'
import './Drawer.css'

/**
 * Panel lateral genérico: portal, overlay, animación de entrada, header con
 * título y botón de cerrar, y un body scrolleable. No sabe nada del contenido.
 *
 * Existe porque los drawers de sucursales, productos, categorías, empleados y
 * productos-por-sucursal repiten este mismo shell en su JSX y comparten el CSS
 * de StaffUserDrawer.css por import cruzado. Ninguno lo tenía extraído, así que
 * no había nada que reusar: el próximo drawer debe partir de acá, no copiar a
 * un hermano. Los cinco existentes pueden migrar después sin apuro.
 *
 * Cerrar: click en el overlay, botón ×, o Escape.
 *
 * El contenido se re-renderiza sin desmontar mientras `open` siga en true, así
 * que cambiar los datos con el panel abierto actualiza sin reanimar la entrada.
 */
export default function Drawer({
  open,
  onClose,
  title,
  subtitle,
  icon,
  width,
  className = '',
  children,
}) {
  useEffect(() => {
    if (!open) return
    function onKey(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  const drawer = (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside
        className={`drawer-panel ${className}`.trim()}
        style={width ? { width } : undefined}
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="drawer-header">
          {icon && <span className="drawer-icon" aria-hidden="true">{icon}</span>}
          <div className="drawer-heading">
            <h2 className="drawer-title">{title}</h2>
            {subtitle && <p className="drawer-subtitle">{subtitle}</p>}
          </div>
          <button type="button" className="drawer-close" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </header>

        <div className="drawer-body">{children}</div>
      </aside>
    </div>
  )

  return ReactDOM.createPortal(drawer, document.body)
}
