import { useState, useEffect } from 'react'
import ReactDOM from 'react-dom'
import useAuth from '../hooks/useAuth'
import { createBackofficeOrder } from '../services/ordersService'
import { fetchBranchMenu } from '../services/catalogService'
import { fetchBranches } from '../services/branchService'
import { canConfirmOrder } from '../utils/ordersUtils'
import './NewOrderModal.css'

function formatPrice(n) {
  return '$' + Number(n).toLocaleString('es-AR', { maximumFractionDigits: 0 })
}

function TrashIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export default function NewOrderModal({ open, onClose }) {
  const { token, branchId } = useAuth()

  const [menuCategories,  setMenuCategories]  = useState([])
  const [menuLoading,     setMenuLoading]     = useState(false)
  const [searchQuery,     setSearchQuery]     = useState('')
  const [cartItems,       setCartItems]       = useState([])
  const [orderType,       setOrderType]       = useState('TAKEAWAY')
  const [deliveryAddress, setDeliveryAddress] = useState('')
  const [paymentMethod,   setPaymentMethod]   = useState('CASH')
  const [customerName,    setCustomerName]    = useState('')
  const [customerPhone,   setCustomerPhone]   = useState('')
  const [notes,           setNotes]           = useState('')
  const [submitting,      setSubmitting]      = useState(false)
  const [error,           setError]           = useState(null)
  const [branchDeliveryFee, setBranchDeliveryFee] = useState(0)
  const [branchServiceFee,  setBranchServiceFee]  = useState(0)

  function resetState() {
    setMenuCategories([])
    setSearchQuery('')
    setCartItems([])
    setOrderType('TAKEAWAY')
    setDeliveryAddress('')
    setPaymentMethod('CASH')
    setCustomerName('')
    setCustomerPhone('')
    setNotes('')
    setError(null)
    setBranchDeliveryFee(0)
    setBranchServiceFee(0)
  }

  function handleClose() {
    if (submitting) return
    resetState()
    onClose()
  }

  useEffect(() => {
    if (!open || !token || !branchId) return
    setMenuLoading(true)
    Promise.all([
      fetchBranchMenu(branchId, token),
      fetchBranches(token).then(list => list.find(b => String(b.id) === String(branchId))),
    ]).then(([menu, branch]) => {
      setMenuCategories(Array.isArray(menu) ? menu : [])
      if (branch) {
        setBranchDeliveryFee(branch.deliveryFee ?? 0)
        setBranchServiceFee(branch.serviceFee ?? 0)
      }
    }).catch(() => {
      setMenuCategories([])
    }).finally(() => {
      setMenuLoading(false)
    })
  }, [open, token, branchId])

  // ── Computed ─────────────────────────────────────────────────

  const filteredMenu = menuCategories
    .map(cat => ({
      ...cat,
      products: cat.products.filter(p =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase())
      ),
    }))
    .filter(cat => cat.products.length > 0)

  const subtotal           = cartItems.reduce((s, i) => s + i.unitPrice * i.quantity, 0)
  const computedDeliveryFee = orderType === 'DELIVERY' ? branchDeliveryFee : 0
  const total              = subtotal + computedDeliveryFee + branchServiceFee
  const itemCount          = cartItems.reduce((s, i) => s + i.quantity, 0)
  const canConfirm         = canConfirmOrder({ cartItems, orderType, deliveryAddress })

  // ── Cart operations ──────────────────────────────────────────

  function addToCart(product) {
    setCartItems(prev => {
      const existing = prev.find(i => i.productId === product.id)
      if (existing) {
        return prev.map(i =>
          i.productId === product.id ? { ...i, quantity: i.quantity + 1 } : i
        )
      }
      return [...prev, {
        productId:   product.id,
        productName: product.name,
        unitPrice:   product.price,
        quantity:    1,
      }]
    })
  }

  function decreaseQuantity(productId) {
    setCartItems(prev => {
      const item = prev.find(i => i.productId === productId)
      if (!item) return prev
      if (item.quantity <= 1) return prev.filter(i => i.productId !== productId)
      return prev.map(i => i.productId === productId ? { ...i, quantity: i.quantity - 1 } : i)
    })
  }

  function increaseQuantity(productId) {
    setCartItems(prev =>
      prev.map(i => i.productId === productId ? { ...i, quantity: i.quantity + 1 } : i)
    )
  }

  function removeFromCart(productId) {
    setCartItems(prev => prev.filter(i => i.productId !== productId))
  }

  // ── Submit ───────────────────────────────────────────────────

  async function handleConfirm() {
    if (!canConfirm || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await createBackofficeOrder({
        branchId: branchId,
        orderType,
        deliveryAddress: orderType === 'DELIVERY' ? deliveryAddress : null,
        paymentMethod,
        customerName: customerName || null,
        customerPhone: customerPhone || null,
        notes: notes || null,
        items:  cartItems.map(i => ({ productId: i.productId, quantity: i.quantity })),
        origin: 'BACKOFFICE',
      }, token)
      window.dispatchEvent(new Event('laroka:order-created'))
      resetState()
      onClose()
    } catch {
      setError('Error al crear el pedido. Intente nuevamente.')
    } finally {
      setSubmitting(false)
    }
  }

  // ── Render ───────────────────────────────────────────────────

  const modal = (
    <div
      className={`nom-backdrop${open ? ' nom-backdrop--open' : ''}`}
      onClick={handleClose}
      aria-hidden={!open}
    >
      <div
        className="nom-dialog"
        onClick={e => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Nueva orden"
      >

        {/* Header */}
        <div className="nom-header">
          <div className="nom-header-left">
            <h2 className="nom-title">Nueva orden</h2>
            {itemCount > 0 && (
              <span className="nom-subtitle">
                MANUAL · {itemCount} PRODUCTO{itemCount !== 1 ? 'S' : ''}
              </span>
            )}
          </div>
          <button
            type="button"
            className="nom-close-btn"
            onClick={handleClose}
            disabled={submitting}
            aria-label="Cerrar"
          >
            ×
          </button>
        </div>

        {/* Body */}
        <div className="nom-body">

          {/* Left: catalog */}
          <div className="nom-col-left">
            <div className="nom-search-box">
              <svg className="nom-search-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="2" />
                <path d="m21 21-4.35-4.35" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <input
                className="nom-search-input"
                type="text"
                placeholder="Buscar producto..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
            </div>

            <div className="nom-catalog">
              {menuLoading ? (
                <div className="nom-catalog-state">Cargando menú...</div>
              ) : filteredMenu.length === 0 ? (
                <div className="nom-catalog-state">
                  {searchQuery ? 'Sin resultados' : 'Menú no disponible'}
                </div>
              ) : (
                filteredMenu.map(cat => (
                  <div key={cat.categoryName} className="nom-category">
                    <div className="nom-category-header">
                      <span className="nom-category-name">{cat.categoryName.toUpperCase()}</span>
                      <span className="nom-category-count">{cat.products.length}</span>
                    </div>
                    {cat.products.map(product => {
                      const cartItem = cartItems.find(i => i.productId === product.id)
                      return (
                        <div
                          key={product.id}
                          className="nom-product-row"
                          onClick={() => addToCart(product)}
                        >
                          <div className="nom-product-info">
                            <span className="nom-product-name">{product.name}</span>
                            {product.description && (
                              <span className="nom-product-desc">{product.description}</span>
                            )}
                          </div>
                          <span className="nom-product-price">{formatPrice(product.price)}</span>
                          <button
                            type="button"
                            className={`nom-add-btn${cartItem ? ' nom-add-btn--active' : ''}`}
                            onClick={e => { e.stopPropagation(); addToCart(product) }}
                            aria-label={`Agregar ${product.name}`}
                          >
                            {cartItem ? cartItem.quantity : '+'}
                          </button>
                        </div>
                      )
                    })}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Divider */}
          <div className="nom-divider" aria-hidden="true" />

          {/* Right: items + form + footer */}
          <div className="nom-col-right">

            {/* Items */}
            <div className="nom-items-section">
              <div className="nom-section-label">PRODUCTOS SELECCIONADOS</div>
              {cartItems.length === 0 ? (
                <div className="nom-cart-empty">Ningún producto seleccionado</div>
              ) : (
                <div className="nom-cart-list">
                  {cartItems.map(item => (
                    <div key={item.productId} className="nom-cart-item">
                      <div className="nom-cart-qty-ctrl">
                        <button
                          type="button"
                          className="nom-qty-btn"
                          onClick={() => decreaseQuantity(item.productId)}
                          aria-label="Disminuir cantidad"
                        >
                          −
                        </button>
                        <span className="nom-qty-value">{item.quantity}</span>
                        <button
                          type="button"
                          className="nom-qty-btn"
                          onClick={() => increaseQuantity(item.productId)}
                          aria-label="Aumentar cantidad"
                        >
                          +
                        </button>
                      </div>
                      <span className="nom-cart-name">{item.productName}</span>
                      <span className="nom-cart-subtotal">
                        {formatPrice(item.unitPrice * item.quantity)}
                      </span>
                      <button
                        type="button"
                        className="nom-cart-trash"
                        onClick={() => removeFromCart(item.productId)}
                        aria-label={`Eliminar ${item.productName}`}
                      >
                        <TrashIcon />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Form */}
            <div className="nom-form-section">

              <div className="nom-field">
                <div className="nom-field-label">MODALIDAD</div>
                <div className="nom-toggle-group">
                  <button
                    type="button"
                    className={`nom-toggle-btn${orderType === 'DELIVERY' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setOrderType('DELIVERY')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M3 11l1-4h8l2 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      <circle cx="5.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
                      <circle cx="18.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
                      <path d="M14 7h4l2 4H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    DELIVERY
                  </button>
                  <button
                    type="button"
                    className={`nom-toggle-btn${orderType === 'TAKEAWAY' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setOrderType('TAKEAWAY')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                      <polyline points="9 22 9 12 15 12 15 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    TAKEAWAY
                  </button>
                </div>
              </div>

              {orderType === 'DELIVERY' && (
                <div className="nom-field">
                  <div className="nom-field-label">DIRECCIÓN</div>
                  <input
                    className="nom-text-input"
                    type="text"
                    placeholder="Dirección de entrega..."
                    value={deliveryAddress}
                    onChange={e => setDeliveryAddress(e.target.value)}
                  />
                </div>
              )}

              <div className="nom-field">
                <div className="nom-field-label">MEDIO DE PAGO</div>
                <div className="nom-toggle-group">
                  <button
                    type="button"
                    className={`nom-toggle-btn${paymentMethod === 'CASH' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setPaymentMethod('CASH')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect x="2" y="6" width="20" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
                      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
                    </svg>
                    EFECTIVO
                  </button>
                  <button
                    type="button"
                    className={`nom-toggle-btn${paymentMethod === 'QR_CODE' ? ' nom-toggle-btn--active' : ''}`}
                    onClick={() => setPaymentMethod('QR_CODE')}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect x="3" y="3" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <rect x="14" y="3" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <rect x="3" y="14" width="7" height="7" rx="1" stroke="currentColor" strokeWidth="1.8" />
                      <path d="M14 14h2v2h-2zM18 14h2M18 18h2v2h-2zM14 18h2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                    </svg>
                    QR
                  </button>
                </div>
              </div>

              <div className="nom-field-row">
                <div className="nom-field">
                  <div className="nom-field-label">NOMBRE</div>
                  <input
                    className="nom-text-input"
                    type="text"
                    placeholder="Nombre del cliente..."
                    value={customerName}
                    onChange={e => setCustomerName(e.target.value)}
                  />
                </div>
                <div className="nom-field">
                  <div className="nom-field-label">TELÉFONO</div>
                  <input
                    className="nom-text-input"
                    type="tel"
                    placeholder="Teléfono..."
                    value={customerPhone}
                    onChange={e => setCustomerPhone(e.target.value)}
                  />
                </div>
              </div>

              <div className="nom-field">
                <div className="nom-field-label">NOTAS</div>
                <textarea
                  className="nom-textarea"
                  placeholder="Notas opcionales para el pedido"
                  value={notes}
                  onChange={e => setNotes(e.target.value)}
                  rows={2}
                />
              </div>

            </div>

            {/* Footer */}
            <div className="nom-footer">
              <div className="nom-totals">
                <div className="nom-total-row">
                  <span className="nom-total-label">Subtotal</span>
                  <span className="nom-total-value">{formatPrice(subtotal)}</span>
                </div>
                {orderType === 'DELIVERY' && computedDeliveryFee > 0 && (
                  <div className="nom-total-row">
                    <span className="nom-total-label">Envío</span>
                    <span className="nom-total-value">{formatPrice(computedDeliveryFee)}</span>
                  </div>
                )}
                {branchServiceFee > 0 && (
                  <div className="nom-total-row">
                    <span className="nom-total-label">Servicio</span>
                    <span className="nom-total-value">{formatPrice(branchServiceFee)}</span>
                  </div>
                )}
                <div className="nom-total-row nom-total-row--grand">
                  <span className="nom-total-grand-label">TOTAL</span>
                  <span className="nom-total-grand-value">{formatPrice(total)}</span>
                </div>
              </div>

              <div className="nom-footer-right">
                {error && <div className="nom-error">{error}</div>}
                <button
                  type="button"
                  className="nom-confirm-btn"
                  onClick={handleConfirm}
                  disabled={!canConfirm || submitting}
                >
                  {submitting ? '···' : 'Confirmar pedido →'}
                </button>
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>
  )

  return ReactDOM.createPortal(modal, document.body)
}
