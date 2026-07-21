import styles from './ProductOptions.module.css'

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

/**
 * Bloque de opciones del detalle de producto. Va debajo de la descripción y antes del
 * precio/cantidad/CTA, y agrupa las variantes que el cliente elige antes de agregar al
 * carrito.
 *
 * No dibuja caja: se integra al flujo de la pantalla y se separa del resto (y cada grupo
 * del siguiente) con la misma línea sutil que usa .detail-separator en el resto del detalle.
 *
 * Está pensado para crecer: cada variante es un hijo hermano. Hoy vive el acordeón de mitad
 * y mitad (US-HH-F-01); el grupo de tamaños (US-SIZE-F-02) entra como <OptionGroup> con un
 * <OptionRadioList> adentro, sin tocar este layout.
 */
export function ProductOptions({ children }) {
  return (
    <section className={styles.block} aria-label="Opciones del producto">
      {children}
    </section>
  )
}

/**
 * Grupo siempre visible, con título. Lo usará el selector de tamaños; el de mitad y mitad
 * usa OptionAccordion porque arranca colapsado.
 */
export function OptionGroup({ title, children }) {
  return (
    <div className={styles.group}>
      {title && <p className={styles.groupTitle}>{title}</p>}
      {children}
    </div>
  )
}

/**
 * Grupo colapsable. La fila entera es el control: al tocarla se expande o colapsa el
 * contenido, y el chevron rota para indicar el estado.
 */
export function OptionAccordion({ id, label, expanded, onToggle, disabled, disabledReason, children }) {
  const panelId = `${id}-panel`
  const isOpen = expanded && !disabled
  return (
    <div className={styles.group}>
      <button
        type="button"
        className={`${styles.accordionRow}${disabled ? ` ${styles.accordionRowDisabled}` : ''}`}
        onClick={() => onToggle(!expanded)}
        aria-expanded={isOpen}
        aria-controls={panelId}
        disabled={disabled}
      >
        <span className={styles.accordionLabel}>{label}</span>
        {/* US-SIZE-F-02: deshabilitada se queda visible, con el motivo al lado — ocultarla
            dejaría al cliente sin saber por qué desapareció una opción que vio antes. */}
        {disabled && disabledReason && (
          <span className={styles.accordionReason}>{disabledReason}</span>
        )}
        <span className={`${styles.chevron}${isOpen ? ` ${styles.chevronOpen}` : ''}`}>
          <ChevronIcon />
        </span>
      </button>
      {isOpen && (
        <div className={styles.accordionPanel} id={panelId}>
          {children}
        </div>
      )}
    </div>
  )
}

/**
 * Lista de opciones excluyentes (radios nativos), separadas por líneas simples — sin caja
 * propia por ítem. La usa el selector de la otra mitad y la reutilizará el de tamaños:
 * misma estructura, sólo cambian `name` y las `options`.
 *
 * Cada opción es `{ id, label, hint }` — `hint` es el texto secundario a la derecha
 * (el precio, en ambos casos).
 */
export function OptionRadioList({ name, legend, options, selectedId, onSelect }) {
  return (
    <fieldset className={styles.fieldset}>
      <legend className={styles.legend}>{legend}</legend>
      <div className={styles.radioList}>
        {options.map((option) => {
          const selected = selectedId === option.id
          return (
            <label
              key={option.id}
              className={`${styles.radioRow}${selected ? ` ${styles.radioRowSelected}` : ''}`}
            >
              <input
                type="radio"
                className={styles.radioInput}
                name={name}
                value={String(option.id)}
                checked={selected}
                onChange={() => onSelect(option.id)}
              />
              <span className={styles.radioDot} aria-hidden="true" />
              <span className={styles.radioLabel}>{option.label}</span>
              {option.hint && <span className={styles.radioHint}>{option.hint}</span>}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
