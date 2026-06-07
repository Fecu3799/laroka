import { useState } from 'react'
import styles from './CheckoutScreen.module.css'
import { usePreferredBranch } from '../hooks/usePreferredBranch'

const _DEBUG_COUNT_KEY = 'laroka_debug_fill_count'

function formatPrice(amount) {
  return `$${Number(amount).toLocaleString('es-AR')}`
}

function BackIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M19 12H5M12 5l-7 7 7 7" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function DeliveryIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 11V17H1V11L3 7H15V17H13" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M15 9L19 7L23 11V17H21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="6" cy="18" r="2" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="18" cy="18" r="2" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  )
}

function RetiroIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 2h12l2 4H4L6 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 6v14a2 2 0 002 2h12a2 2 0 002-2V6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M9 11h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function ChevronDownIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function EfectivoIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2" y="6" width="20" height="13" rx="2" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="12" cy="12.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
      <path d="M6 9v7M18 9v7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function ArrowRightIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function MercadoPagoIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" />
      <path d="M7 15V9l3.5 4L14 9v6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CheckoutScreen({ onBack, onConfirm, items = [], initialData = null }) {
  const { deliveryFee, serviceFee } = usePreferredBranch()
  const [orderType, setOrderType] = useState(initialData?.orderType || 'delivery')
  const [nombre, setNombre] = useState(initialData?.nombre || '')
  const [telefono, setTelefono] = useState(initialData?.telefono || '')
  const [direccion, setDireccion] = useState(initialData?.direccion || '')
  const [notas, setNotas] = useState(initialData?.notas || '')
  const [paymentMethod, setPaymentMethod] = useState('efectivo')
  const [summaryOpen, setSummaryOpen] = useState(false)
  const [errors, setErrors] = useState({ nombre: '', telefono: '', direccion: '' })
  const [submitting, setSubmitting] = useState(false)
  const [mpRedirecting, setMpRedirecting] = useState(false)
  const [mpError, setMpError] = useState(null)

  const isDelivery = orderType === 'delivery'
  const isEfectivo = paymentMethod === 'efectivo'

  const subtotal = items.reduce((sum, item) => sum + item.price * item.qty, 0)
  const totalQty = items.reduce((sum, item) => sum + item.qty, 0)
  const total = subtotal + Number(serviceFee) + (isDelivery ? Number(deliveryFee) : 0)

  const isFormValid = nombre.trim() && telefono.trim() && (!isDelivery || direccion.trim())

  const handleDebugFill = import.meta.env.DEV
    ? () => {
        const next = (parseInt(localStorage.getItem(_DEBUG_COUNT_KEY) || '0', 10)) + 1
        localStorage.setItem(_DEBUG_COUNT_KEY, String(next))
        const ts = new Date().toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
        setNombre(`Dev User #${next}`)
        setTelefono('2804000000')
        setDireccion('Av. Roca 123, Puerto Madryn')
        setNotas(`[DEBUG #${next} · ${ts}]`)
        setErrors({ nombre: '', telefono: '', direccion: '' })
      }
    : null

  const handleConfirm = async () => {
    const newErrors = {
      nombre: nombre.trim() ? '' : 'Ingresá tu nombre',
      telefono: telefono.trim() ? '' : 'Ingresá tu teléfono',
      direccion: isDelivery && !direccion.trim() ? 'Ingresá la dirección de entrega' : '',
    }
    setErrors(newErrors)
    if (Object.values(newErrors).some(Boolean)) return
    setMpError(null)
    setSubmitting(true)
    if (!isEfectivo) setMpRedirecting(true)
    try {
      await onConfirm({
        orderType,
        nombre: nombre.trim(),
        telefono: telefono.trim(),
        direccion: direccion.trim(),
        notas: notas.trim(),
        paymentMethod: isEfectivo ? 'CASH' : 'MERCADOPAGO',
      })
    } catch {
      if (!isEfectivo) {
        setMpRedirecting(false)
        setMpError('Hubo un error al iniciar el pago. Intentá nuevamente.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={styles.screen}>
      {mpRedirecting && (
        <div className={styles.mpOverlay} aria-live="polite" aria-label="Redirigiendo a MercadoPago">
          <div className={styles.mpSpinner} />
          <span className={styles.mpOverlayText}>Redirigiendo a MercadoPago</span>
        </div>
      )}
      <div className={styles.scrollArea}>
        {/* Toggle Delivery / Retirar + botón volver */}
        <div className={styles.toggleRow}>
          <button className={styles.backBtn} aria-label="Volver" onClick={onBack}>
            <BackIcon />
          </button>
          {import.meta.env.DEV && (
            <button
              type="button"
              className={styles.debugFillBtn}
              onClick={handleDebugFill}
              title="Rellenar campos con datos de prueba"
            >
              🛠 Fill Debug Data
            </button>
          )}
          <button
            className={isDelivery ? styles.toggleBtnActive : styles.toggleBtnInactive}
            aria-pressed={isDelivery}
            onClick={() => setOrderType('delivery')}
          >
            <DeliveryIcon />
            Delivery
          </button>
          <button
            className={!isDelivery ? styles.toggleBtnActive : styles.toggleBtnInactive}
            aria-pressed={!isDelivery}
            onClick={() => setOrderType('takeaway')}
          >
            <RetiroIcon />
            Retirar
          </button>
        </div>

        {/* Tus datos */}
        <div className={styles.card}>
          <p className={styles.sectionLabel}>TUS DATOS</p>
          <div className={styles.inputRow}>
            <div className={styles.inputGroup}>
              <label className={styles.inputLabel}>Nombre</label>
              <input
                className={styles.input}
                placeholder="¿Cómo te llamás?"
                value={nombre}
                onChange={e => { setNombre(e.target.value); setErrors(prev => ({ ...prev, nombre: '' })) }}
              />
              {errors.nombre && <span className={styles.fieldError}>{errors.nombre}</span>}
            </div>
            <div className={styles.inputGroup}>
              <label className={styles.inputLabel}>Teléfono</label>
              <input
                className={styles.input}
                placeholder="11 0000-0000"
                value={telefono}
                onChange={e => { setTelefono(e.target.value); setErrors(prev => ({ ...prev, telefono: '' })) }}
              />
              {errors.telefono && <span className={styles.fieldError}>{errors.telefono}</span>}
            </div>
          </div>
          <div className={styles.inputGroupFull} style={{ display: isDelivery ? undefined : 'none' }}>
            <label className={styles.inputLabel}>Dirección de entrega</label>
            <input
              className={styles.input}
              placeholder="Calle y número, piso, depto"
              value={direccion}
              onChange={e => { setDireccion(e.target.value); setErrors(prev => ({ ...prev, direccion: '' })) }}
            />
            {errors.direccion && <span className={styles.fieldError}>{errors.direccion}</span>}
          </div>
        </div>

        {/* Aclaraciones */}
        <div className={styles.card}>
          <p className={styles.sectionLabel}>ACLARACIONES</p>
          <textarea
            className={styles.textarea}
            placeholder="Sin ingredientes, extra salsa, tocar timbre..."
            rows={2}
            value={notas}
            onChange={e => setNotas(e.target.value)}
          />
        </div>

        {/* Resumen del pedido */}
        <div className={styles.summaryCard}>
          <button className={styles.summaryHeader} onClick={() => setSummaryOpen(o => !o)}>
            <span className={styles.summaryTitle}>Resumen del pedido</span>
            <span className={styles.summaryMeta}>{totalQty} producto{totalQty !== 1 ? 's' : ''} · {formatPrice(subtotal)}</span>
            <span className={`${styles.summaryChevron}${summaryOpen ? ` ${styles.summaryChevronOpen}` : ''}`}>
              <ChevronDownIcon />
            </span>
          </button>
          {summaryOpen && (
            <div className={styles.summaryBody}>
              {items.map((item, i) => (
                <div key={i} className={styles.summaryItemRow}>
                  <span className={styles.summaryItemQtyName}>{item.qty}× {item.name}</span>
                  <span className={styles.summaryItemPrice}>{formatPrice(item.price * item.qty)}</span>
                </div>
              ))}
              <div className={styles.summarySeparator} />
              <div className={styles.summaryFeeRow}>
                <span className={styles.summaryFeeLabel}>Subtotal</span>
                <span className={styles.summaryFeeValue}>{formatPrice(subtotal)}</span>
              </div>
              <div className={styles.summaryFeeRow}>
                <span className={styles.summaryFeeLabel}>Cargo de servicio</span>
                <span className={styles.summaryFeeValue}>{formatPrice(serviceFee)}</span>
              </div>
              {isDelivery && (
                <div className={styles.summaryFeeRow}>
                  <span className={styles.summaryFeeLabel}>Cargo de delivery</span>
                  <span className={styles.summaryFeeValue}>{formatPrice(deliveryFee)}</span>
                </div>
              )}
              <div className={styles.summaryTotalRow}>
                <span className={styles.summaryTotalLabel}>TOTAL</span>
                <span className={styles.summaryTotalAmount}>{formatPrice(total)}</span>
              </div>
            </div>
          )}
        </div>

        {/* Medio de pago */}
        <div className={styles.paymentSection}>
          <p className={styles.sectionLabel}>MEDIO DE PAGO</p>
          <div className={styles.paymentRow}>
            <button
              className={isEfectivo ? styles.paymentBtnActive : styles.paymentBtnInactive}
              aria-pressed={isEfectivo}
              onClick={() => setPaymentMethod('efectivo')}
            >
              <span className={styles.paymentIcon}><EfectivoIcon /></span>
              <span className={styles.paymentName}>Efectivo</span>
              <span className={styles.paymentSub}>{isDelivery ? 'Al recibir' : 'Al retirar'}</span>
            </button>
            <button
              className={!isEfectivo ? styles.paymentBtnActive : styles.paymentBtnInactive}
              aria-pressed={!isEfectivo}
              onClick={() => setPaymentMethod('mercadopago')}
            >
              <span className={styles.paymentIcon}><MercadoPagoIcon /></span>
              <span className={styles.paymentName}>MercadoPago</span>
              <span className={styles.paymentSub}>Pago online</span>
            </button>
          </div>
        </div>
      </div>

      {/* CTA fijo */}
      <div className={styles.ctaWrapper}>
        {mpError && (
          <p className={styles.mpError}>{mpError}</p>
        )}
        <div className={styles.ctaTotalRow}>
          <span className={styles.ctaTotalLabel}>TOTAL</span>
          <span className={styles.ctaTotalAmount}>{formatPrice(total)}</span>
        </div>
        <button
          className={styles.ctaBtn}
          style={(!isFormValid || submitting || mpRedirecting) ? { opacity: 0.5, pointerEvents: 'none' } : undefined}
          onClick={handleConfirm}
        >
          <span className={styles.ctaBtnText}>
            {submitting && !isEfectivo ? 'PROCESANDO...' : isEfectivo ? 'CONFIRMAR PEDIDO' : 'IR A PAGAR'}
          </span>
          {!(submitting && !isEfectivo) && <ArrowRightIcon />}
        </button>
      </div>
    </div>
  )
}
