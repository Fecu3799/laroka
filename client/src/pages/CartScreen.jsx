import { useState, useEffect, useCallback } from 'react'
import { CheckoutScreen } from './CheckoutScreen'
import { ConfirmationScreen } from './ConfirmationScreen'
import { PendingPaymentModal } from '../features/payment/PaymentModals'
import { usePreferredBranch } from '../hooks/usePreferredBranch'
import { usePushSubscription } from '../hooks/usePushSubscription'
import { PushPermissionSheet } from '../components/PushPermissionSheet'
import { addActiveOrder } from '../utils/activeOrders'
import { initiatePayment } from '../services/paymentsService'
import { apiFetch, showToast } from '../services/http'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// Timeout de seguridad de la resolución de suscripción push. La resolución hace
// awaits sobre el Service Worker / pushManager / red que, en estados transitorios,
// pueden no resolver nunca y trabar la creación del pedido. Si se cumple el
// timeout seguimos con pushSubscriptionId = null: el push es best-effort y jamás
// debe bloquear ni revertir el POST /orders (aplica a efectivo y MercadoPago).
const PUSH_RESOLVE_TIMEOUT_MS = 3000

// Corre `promise` con un límite de tiempo. Nunca lanza ni deja rejections colgadas:
// resuelve { timedOut, value }. Si gana el timeout, el settle posterior de la
// promesa original se ignora (el fetch de push ya se autocancela por su AbortController).
function resolveWithTimeout(promise, ms) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve({ timedOut: true, value: null }), ms)
    promise.then(
      (value) => { clearTimeout(timer); resolve({ timedOut: false, value }) },
      () => { clearTimeout(timer); resolve({ timedOut: false, value: null }) },
    )
  })
}

function formatPrice(price) {
  return `$${Number(price).toLocaleString('es-AR')}`
}


function TrashIcon({ size = 14 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

function ArrowRightIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

function EmptyCartIcon() {
  return (
    <svg width="80" height="80" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
      <line x1="3" y1="6" x2="21" y2="6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
      <path d="M16 10a4 4 0 01-8 0" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  )
}

function CartItemImage({ src, alt }) {
  const [error, setError] = useState(false)
  if (!src || error) {
    return <div className="cart-item-img cart-item-img--placeholder" aria-hidden="true" />
  }
  return (
    <img src={src} alt={alt} className="cart-item-img" onError={() => setError(true)} />
  )
}

function CartItemRow({ item, onRemoveRequest, onUpdateQty }) {
  const [removing, setRemoving] = useState(false)

  const handleRemove = () => {
    setRemoving(true)
    setTimeout(() => onRemoveRequest(item.id), 240)
  }

  return (
    <div className={`cart-item${removing ? ' cart-item--removing' : ''}`}>
      <div className="cart-item-img-wrapper">
        <CartItemImage src={item.imageUrl} alt={item.name} />
      </div>
      <div className="cart-item-info">
        <span className="cart-item-name">{item.name}</span>
        {item.description && (
          <span className="cart-item-desc">{item.description}</span>
        )}
        <div className="cart-item-bottom">
          <div className="cart-item-qty-control" role="group" aria-label="Cantidad">
            <button
              className="cart-item-qty-btn"
              onClick={() => onUpdateQty(item.id, item.qty - 1)}
              disabled={item.qty <= 1}
              aria-label="Reducir cantidad"
            >
              −
            </button>
            <span className="cart-item-qty-number" aria-live="polite">{item.qty}</span>
            <button
              className="cart-item-qty-btn"
              onClick={() => onUpdateQty(item.id, item.qty + 1)}
              aria-label="Aumentar cantidad"
            >
              +
            </button>
          </div>
          <span className="cart-item-subtotal">{formatPrice(item.price * item.qty)}</span>
        </div>
      </div>
      <button
        className="cart-item-delete"
        onClick={handleRemove}
        aria-label={`Eliminar ${item.name}`}
      >
        <TrashIcon size={14} />
      </button>
    </div>
  )
}

function ExtraCardThumb({ src, alt }) {
  const [error, setError] = useState(false)
  if (!src || error) {
    return <div className="extra-card-thumb extra-card-thumb--placeholder" aria-hidden="true" />
  }
  return (
    <img src={src} alt={alt} className="extra-card-thumb" onError={() => setError(true)} />
  )
}

function ExtraCard({ extra, cartQty, onAdd }) {
  const [showCheck, setShowCheck] = useState(false)

  const handleAdd = () => {
    onAdd(extra)
    setShowCheck(true)
    setTimeout(() => setShowCheck(false), 1200)
  }

  return (
    <div className="extra-card">
      <div className="extra-card-thumb-wrapper">
        <ExtraCardThumb src={extra.imageUrl} alt={extra.name} />
        <button
          className={`extra-card-add-btn${showCheck ? ' extra-card-add-btn--added' : ''}`}
          onClick={handleAdd}
          aria-label={`Agregar ${extra.name}`}
        >
          {showCheck ? '✓' : '+'}
        </button>
        {cartQty > 0 && (
          <div className="extra-card-badge">×{cartQty}</div>
        )}
      </div>
      <span className="extra-card-name">{extra.name}</span>
      <span className="extra-card-price">{formatPrice(extra.price)}</span>
    </div>
  )
}

export function CartScreen({ items, extras = [], onBack, onRemove, onUpdateQty, onClear, onAddExtra, paymentFailure = null, onPaymentFailureConsumed = () => {}, pendingPayment = null, onPendingPaymentConsumed = () => {} }) {
  const { preferredBranchId } = usePreferredBranch()
  const {
    sheet: pushSheet,
    acceptSheet: acceptPushSheet,
    dismissSheet: dismissPushSheet,
    getOrCreateSubscription,
    requestPermissionAndSubscribe,
    showInstallInstructions,
  } = usePushSubscription()
  const [confirmClear, setConfirmClear] = useState(false)
  const [showCheckout, setShowCheckout] = useState(() => !!paymentFailure)
  const [confirmedOrderId, setConfirmedOrderId] = useState(null)
  const [showFailureModal, setShowFailureModal] = useState(() => !!paymentFailure)
  const [failureOrderId] = useState(() => paymentFailure?.orderId || null)
  const [checkoutInitialData, setCheckoutInitialData] = useState(() => paymentFailure?.formData || null)
  const [showPendingModal, setShowPendingModal] = useState(() => !!pendingPayment)
  const [pendingOrderId] = useState(() => pendingPayment?.orderId || null)
  const [mpReturnOrderId, setMpReturnOrderId] = useState(null)

  useEffect(() => {
    if (!paymentFailure) return
    onPaymentFailureConsumed()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paymentFailure])


  const handleMpReturn = useCallback(async (orderId) => {
    if (import.meta.env.VITE_DEV_MP_DEBUG_LOGS === 'true') console.log('[MP-DEBUG] handleMpReturn called — orderId:', orderId)
    if (!orderId) return
    setMpReturnOrderId(null)
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/status`)
      if (!res.ok) return
      const data = await res.json()
      if (import.meta.env.VITE_DEV_MP_DEBUG_LOGS === 'true') console.log('[MP-DEBUG] order status response — status:', data.status)
      if (data.status === 'PENDING_PAYMENT') {
        setMpReturnOrderId(orderId)
      } else if (data.status && data.status !== 'CANCELLED') {
        sessionStorage.removeItem('laroka_checkout_recovery')
        onClear()
        setConfirmedOrderId(orderId)
      }
    } catch { /* fetch fallido — usuario ve el checkout sin cambios */ }
  }, [onClear])

  // Decide, según soporte/permiso/plataforma, cómo obtener el subscriptionId
  // antes de crear el pedido. Nunca bloquea ni lanza: si no hay push, retorna null.
  const resolvePushSubscriptionId = async () => {
    // US-09-F-04: en iOS el Web Push solo funciona desde la PWA instalada en la
    // pantalla de inicio. Si la app corre en Safari (no standalone) sobre
    // iPhone/iPad, la suscripción es imposible: mostramos las instrucciones de
    // instalación paso a paso y NO llamamos a requestPermission() — en iOS Safari
    // fallaría en silencio. El sheet se autolimita a una vez por sesión
    // (ver showInstallInstructions en usePushSubscription).
    const ua = navigator.userAgent.toLowerCase()
    const iosNotInstalled = !navigator.standalone && /iphone|ipad/i.test(ua)
    if (iosNotInstalled) {
      await showInstallInstructions()
      return null
    }

    if (typeof Notification === 'undefined') return null

    const permission = Notification.permission
    if (permission === 'denied') return null
    if (permission === 'granted') return getOrCreateSubscription()
    // permission === 'default'
    return requestPermissionAndSubscribe()
  }

  const handleConfirm = async (formData) => {
    // La resolución de push corre con un timeout de seguridad: si tarda más de
    // PUSH_RESOLVE_TIMEOUT_MS seguimos el flujo con pushSubscriptionId = null y
    // avisamos por toast, sin bloquear ni revertir la creación del pedido.
    const { timedOut, value } = await resolveWithTimeout(
      resolvePushSubscriptionId(),
      PUSH_RESOLVE_TIMEOUT_MS,
    )
    if (timedOut) {
      showToast('Hubo un problema con las notificaciones. Tu pedido se está creando igual.')
    }
    const pushSubscriptionId = timedOut ? null : value
    const payload = {
      branchId: preferredBranchId,
      orderType: formData.orderType === 'delivery' ? 'DELIVERY' : 'TAKEAWAY',
      deliveryAddress: formData.direccion || null,
      notes: formData.notas || null,
      customerName: formData.nombre || null,
      customerPhone: formData.telefono || null,
      paymentMethod: formData.paymentMethod,
      items: items.map(i => ({ productId: i.id, quantity: i.qty })),
      ...(pushSubscriptionId ? { pushSubscriptionId } : {}),
    }
    let res
    try {
      res = await apiFetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    } catch (err) {
      // apiFetch ya mostró el toast con el mensaje del backend (indica qué
      // producto no está disponible). El 422 de producto no disponible trae el
      // productId como campo estructurado (US-15-CF-05): lo removemos del carrito
      // para que el reintento no vuelva a fallar por lo mismo.
      if (err?.status === 422 && err.body?.productId != null) {
        onRemove(err.body.productId)
        // US-17-CF-01: además de removerlo, volver a la pantalla del carrito
        // (no quedarse en checkout) para que el cliente vea el error y reintente
        // desde el carrito ya corregido.
        setShowCheckout(false)
      }
      throw err
    }
    const data = await res.json()
    onClear()

    if (formData.paymentMethod === 'MERCADOPAGO') {
      addActiveOrder(data.orderId, preferredBranchId)
      sessionStorage.setItem('laroka_checkout_recovery', JSON.stringify({
        orderId: data.orderId,
        items: items.map(i => ({
          id: i.id, name: i.name, price: i.price, qty: i.qty,
          imageUrl: i.imageUrl || null, description: i.description || null,
        })),
        formData: {
          orderType: formData.orderType,
          nombre: formData.nombre,
          telefono: formData.telefono,
          direccion: formData.direccion,
          notas: formData.notas,
        },
      }))
      const paymentLink = await initiatePayment(data.orderId)
      window.location.href = paymentLink
      return
    }

    setConfirmedOrderId(data.orderId)
  }

  if (confirmedOrderId) {
    return <ConfirmationScreen orderId={confirmedOrderId} branchId={preferredBranchId} onComplete={onBack} />
  }

  if (showCheckout) {
    return (
      <>
        <CheckoutScreen
          items={items}
          onBack={() => { setShowCheckout(false); setCheckoutInitialData(null) }}
          onConfirm={handleConfirm}
          initialData={checkoutInitialData}
          onMpReturn={handleMpReturn}
        />
        {showFailureModal && failureOrderId && (
          <PendingPaymentModal
            orderId={failureOrderId}
            onCancel={() => { setShowFailureModal(false); setShowCheckout(false) }}
          />
        )}
        {mpReturnOrderId && (
          <PendingPaymentModal
            orderId={mpReturnOrderId}
            onCancel={() => {
              setMpReturnOrderId(null)
              setShowCheckout(false)
            }}
          />
        )}
        <PushPermissionSheet
          open={pushSheet.open}
          variant={pushSheet.variant}
          onAccept={acceptPushSheet}
          onDismiss={dismissPushSheet}
        />
      </>
    )
  }

  const total = items.reduce((sum, i) => sum + i.price * i.qty, 0)
  const count = items.reduce((sum, i) => sum + i.qty, 0)

  if (items.length === 0) {
    return (
      <>
        {showPendingModal && pendingOrderId && (
          <PendingPaymentModal
            orderId={pendingOrderId}
            onCancel={() => { setShowPendingModal(false); onPendingPaymentConsumed() }}
          />
        )}
        <div className="cart-empty">
          <div className="cart-empty-icon">
            <EmptyCartIcon />
          </div>
          <h2 className="cart-empty-title">Tu carrito está vacío</h2>
          <p className="cart-empty-desc">
            Agregá productos desde el menú para armar tu pedido.
          </p>
          <button className="cart-back-to-menu-btn" onClick={onBack}>
            Volver al menú
          </button>
        </div>
      </>
    )
  }

  return (
    <div className="cart-content">
      {showPendingModal && pendingOrderId && (
        <PendingPaymentModal
          orderId={pendingOrderId}
          onCancel={() => { setShowPendingModal(false); onPendingPaymentConsumed() }}
        />
      )}
      <div className="cart-scroll-area">
        <div className="cart-counter">
          {count} producto{count !== 1 ? 's' : ''} en tu pedido
        </div>

        <div className="cart-list">
          {items.map(item => (
            <CartItemRow
              key={item.id}
              item={item}
              onRemoveRequest={onRemove}
              onUpdateQty={onUpdateQty}
            />
          ))}
        </div>

        {!confirmClear ? (
          <button className="cart-clear-btn" onClick={() => setConfirmClear(true)}>
            <TrashIcon size={13} />
            Vaciar carrito
          </button>
        ) : (
          <div className="cart-clear-confirm">
            <span className="cart-clear-confirm-text">¿Seguro?</span>
            <button
              className="cart-confirm-yes"
              onClick={() => { onClear(); setConfirmClear(false) }}
            >
              Sí, vaciar
            </button>
            <button
              className="cart-confirm-cancel"
              onClick={() => setConfirmClear(false)}
            >
              Cancelar
            </button>
          </div>
        )}

        <div className="cart-extras">
          <div className="cart-extras-separator" />
          <h3 className="cart-extras-title">¿Se te antoja algo extra?</h3>
          <div className="cart-extras-scroll">
            {extras.map(extra => (
              <ExtraCard
                key={extra.id}
                extra={extra}
                cartQty={items.find(i => i.id === extra.id)?.qty || 0}
                onAdd={onAddExtra}
              />
            ))}
          </div>
        </div>
      </div>

      <div className="cart-subfooter">
        <div className="cart-subtotal-row">
          <span className="cart-subtotal-label">SUBTOTAL</span>
          <span className="cart-subtotal-amount">{formatPrice(total)}</span>
        </div>
        <button className="cart-pay-btn" onClick={() => setShowCheckout(true)}>
          IR A PAGAR <ArrowRightIcon />
        </button>
      </div>
    </div>
  )
}
