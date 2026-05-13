import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import useOrders from '../hooks/useOrders'
import { advanceOrderStatus } from '../services/ordersService'
import './Orders.css'

const API_URL = import.meta.env.VITE_API_URL ?? ''

// ── Constants ─────────────────────────────────────────────────

const TERMINAL = new Set(['DELIVERED', 'CANCELLED'])

const STATUS_CONFIG = {
  PENDING_PAYMENT:  { label: 'Pago pendiente', bg: '#6b672e', color: '#c5cda7', border: '#4a6b50' },
  RECEIVED:         { label: 'Recibido',       bg: '#1d3557', color: '#90bdf9', border: '#2a4a80' },
  IN_PREPARATION:   { label: 'En preparación', bg: '#2d1f00', color: '#fbbf24', border: '#5c3d00' },
  ON_THE_WAY:       { label: 'En camino',      bg: '#2d1047', color: '#c084fc', border: '#5a1f8a' },
  READY_FOR_PICKUP: { label: 'Para retirar',   bg: '#0a2e14', color: '#4ade80', border: '#1a5c2c' },
  DELIVERED:        { label: 'Entregado',      bg: '#0a2e14', color: '#4ade80', border: '#1a5c2c' },
  CANCELLED:        { label: 'Cancelado',      bg: '#2e0f0f', color: '#f87171', border: '#5c1f1f' },
}

const PAYMENT_STATUS_LABEL = {
  APPROVED:  'Pagado',
  PENDING:   'Pendiente',
  REJECTED:  'Rechazado',
  CANCELLED: 'Cancelado',
}

const PAYMENT_STATUS_COLOR = {
  APPROVED:  '#4ade80',
  PENDING:   '#fb923c',
  REJECTED:  '#f87171',
  CANCELLED: '#f87171',
}

// eslint-disable-next-line no-unused-vars
const PAYMENT_METHOD_LABEL = {
  MERCADOPAGO: 'MercadoPago',
  CASH:        'Efectivo',
}

const TABS = [
  { key: 'ALL',            label: 'Todos' },
  { key: 'RECEIVED',       label: 'Recibidos' },
  { key: 'IN_PREPARATION', label: 'En preparación' },
  { key: 'ON_THE_WAY',     label: 'En camino' },
  { key: 'DELIVERED',      label: 'Entregados' },
  { key: 'CANCELLED',      label: 'Cancelados' },
]

const STATUS_CHIPS = [
  { key: 'RECEIVED',       label: 'Recibidos',  bg: '#1d3557', color: '#90bdf9', border: '#2a4a80' },
  { key: 'IN_PREPARATION', label: 'En prep.',   bg: '#2d1f00', color: '#fbbf24', border: '#5c3d00' },
  { key: 'ON_THE_WAY',     label: 'En camino',  bg: '#2d1047', color: '#c084fc', border: '#5a1f8a' },
  { key: 'DELIVERED',      label: 'Entregados', bg: '#0a2e14', color: '#4ade80', border: '#1a5c2c' },
]

// ── Helpers ───────────────────────────────────────────────────

function shortId(id) {
  return '#' + id.replace(/-/g, '').slice(0, 4).toUpperCase()
}

function formatTime(createdAt) {
  if (!createdAt) return '—'
  const date = new Date(createdAt)
  const now = new Date()
  const timeStr = date.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })
  if (date.toDateString() === now.toDateString()) return `Hoy · ${timeStr}`
  return date.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit' }) + ` · ${timeStr}`
}

function getInitials(name) {
  if (!name) return '?'
  return name.trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase()
}

function getNextStatus(status, orderType) {
  if (status === 'RECEIVED') return 'IN_PREPARATION'
  if (status === 'IN_PREPARATION') return orderType === 'DELIVERY' ? 'ON_THE_WAY' : 'READY_FOR_PICKUP'
  if (status === 'ON_THE_WAY' || status === 'READY_FOR_PICKUP') return 'DELIVERED'
  return null
}

function filterOrders(orders, activeTab, searchQuery) {
  let list = orders
  if (activeTab !== 'ALL') {
    if (activeTab === 'ON_THE_WAY') {
      list = list.filter(o => o.status === 'ON_THE_WAY' || o.status === 'READY_FOR_PICKUP')
    } else {
      list = list.filter(o => o.status === activeTab)
    }
  }
  if (searchQuery.trim()) {
    const q = searchQuery.toLowerCase()
    list = list.filter(o =>
      shortId(o.id).toLowerCase().includes(q) ||
      (o.customerName && o.customerName.toLowerCase().includes(q))
    )
  }
  return list
}

function sortOrders(orders) {
  return [...orders].sort((a, b) => {
    const aT = TERMINAL.has(a.status)
    const bT = TERMINAL.has(b.status)
    if (aT !== bT) return aT ? 1 : -1
    return new Date(b.createdAt) - new Date(a.createdAt)
  })
}

function tabCount(orders, key) {
  if (key === 'ALL') return orders.length
  if (key === 'ON_THE_WAY') return orders.filter(o => o.status === 'ON_THE_WAY' || o.status === 'READY_FOR_PICKUP').length
  return orders.filter(o => o.status === key).length
}

// ── Inline icons ──────────────────────────────────────────────

function SearchIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="11" cy="11" r="8" stroke="#7a9b80" strokeWidth="2" />
      <path d="m21 21-4.35-4.35" stroke="#7a9b80" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function RefreshIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M1 4v6h6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3.51 15a9 9 0 1 0 .49-4.95" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  )
}

function PhoneIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 1.27h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L7.91 8.91a16 16 0 0 0 6 6l.91-.91a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" stroke="#4a6b50" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function PinIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" stroke="#4a6b50" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="12" cy="10" r="3" stroke="#4a6b50" strokeWidth="2" />
    </svg>
  )
}

function ScooterIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 11l1-4h8l2 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="5.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
      <circle cx="18.5" cy="15.5" r="2.5" stroke="currentColor" strokeWidth="1.8" />
      <path d="M14 7h4l2 4H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function StoreIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <polyline points="9 22 9 12 15 12 15 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

// ── Main component ────────────────────────────────────────────

export default function Orders() {
  const navigate = useNavigate()
  const { token } = useAuth()
  const { orders, loading, error, newOrderCount, refresh, incrementNewOrders, dismissOrder, updateOrderInList } =
    useOrders(token)
  const [activeTab,   setActiveTab]   = useState('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedId,  setSelectedId]  = useState(null)
  const [advancing,   setAdvancing]   = useState(null)

  // ── SSE ─────────────────────────────────────────────────────
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
            if (res.status === 401 || res.status === 403) { active = false; return }
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
                  if (json.type === 'NEW_ORDER') incrementNewOrders()
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
  }, [token, incrementNewOrders])

  // ── handleAdvance ────────────────────────────────────────────
  const handleAdvance = useCallback(async (e, order) => {
    e.stopPropagation()
    const next = getNextStatus(order.status, order.orderType)
    if (!next) return
    setAdvancing(order.id)
    try {
      await advanceOrderStatus(order.id, next, token)
      updateOrderInList(order.id, next)
    } catch { /* silent */ }
    finally { setAdvancing(null) }
  }, [token, updateOrderInList])

  // ── Derived values ───────────────────────────────────────────
  const chipCounts = {
    RECEIVED:       orders.filter(o => o.status === 'RECEIVED').length,
    IN_PREPARATION: orders.filter(o => o.status === 'IN_PREPARATION').length,
    ON_THE_WAY:     orders.filter(o => o.status === 'ON_THE_WAY' || o.status === 'READY_FOR_PICKUP').length,
    DELIVERED:      orders.filter(o => o.status === 'DELIVERED').length,
  }
  const activeCount    = orders.filter(o => !TERMINAL.has(o.status)).length
  const visibleOrders  = sortOrders(filterOrders(orders, activeTab, searchQuery))

  // ── Render ───────────────────────────────────────────────────
  return (
    <div className="orders-page">

      {/* ── Top bar ──────────────────────────────────────────── */}
      <div className="orders-header">
        <div className="orders-header-title-block">
          <h1 className="orders-title">Pedidos</h1>
          <span className="orders-subtitle">
            {orders.length} pedidos totales · {activeCount} activos
          </span>
        </div>

        <div className="orders-header-spacer" />

        <div className="orders-chips">
          {STATUS_CHIPS.map(chip => (
            <div
              key={chip.key}
              className="orders-chip"
              style={{ backgroundColor: chip.bg, color: chip.color, borderColor: chip.border }}
            >
              <span className="orders-chip-count">{chipCounts[chip.key] ?? 0}</span>
              <span className="orders-chip-label">{chip.label}</span>
            </div>
          ))}
        </div>

        <div className="orders-search">
          <SearchIcon />
          <input
            className="orders-search-input"
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Buscar #ID o cliente..."
          />
        </div>

        <button
          className={`orders-refresh-btn${newOrderCount > 0 ? ' orders-refresh-btn--notify' : ''}`}
          type="button"
          onClick={refresh}
        >
          <RefreshIcon />
          Actualizar lista
          {newOrderCount > 0 && (
            <span className="orders-refresh-badge">{newOrderCount}</span>
          )}
        </button>
      </div>

      {/* ── Tabs ─────────────────────────────────────────────── */}
      <div className="orders-tabs">
        {TABS.map(tab => (
          <button
            key={tab.key}
            type="button"
            className={`orders-tab${activeTab === tab.key ? ' orders-tab--active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
            <span className={`orders-tab-pill${activeTab === tab.key ? ' orders-tab-pill--active' : ''}`}>
              {tabCount(orders, tab.key)}
            </span>
          </button>
        ))}
      </div>

      {/* ── Main area ────────────────────────────────────────── */}
      <div className="orders-layout">
        {loading ? (
          <div className="orders-state-center">
            <div className="orders-spinner" />
          </div>
        ) : error ? (
          <div className="orders-state-center orders-state-error">
            {error}
          </div>
        ) : (
          <div className="orders-list-col">

            <div className="orders-table-head">
              <div className="col-head col-head--order">Pedido</div>
              <div className="col-head col-head--customer">Cliente</div>
              <div className="col-head col-head--items">Productos</div>
              <div className="col-head col-head--notes">Notas</div>
              <div className="col-head col-head--status">Estado</div>
              <div className="col-head col-head--action">Acción</div>
            </div>

            <div className="orders-rows">
              {visibleOrders.length === 0 ? (
                <div className="orders-empty">
                  No hay pedidos en esta categoría
                </div>
              ) : (
                visibleOrders.map(order => (
                  <OrderRow
                    key={order.id}
                    order={order}
                    isSelected={order.id === selectedId}
                    advancing={advancing}
                    onSelect={() => setSelectedId(order.id)}
                    onAdvance={e => handleAdvance(e, order)}
                    onDismiss={e => { e.stopPropagation(); dismissOrder(order.id) }}
                  />
                ))
              )}
            </div>

          </div>
        )}
      </div>

    </div>
  )
}

// ── OrderRow ──────────────────────────────────────────────────

function OrderRow({ order, isSelected, advancing, onSelect, onAdvance, onDismiss }) {
  const cfg        = STATUS_CONFIG[order.status] ?? {}
  const next       = getNextStatus(order.status, order.orderType)
  const isCancelled = order.status === 'CANCELLED'

  return (
    <div
      className={[
        'orders-row',
        isSelected  ? 'orders-row--selected'  : '',
        isCancelled ? 'orders-row--cancelled' : '',
      ].filter(Boolean).join(' ')}
      onClick={onSelect}
    >

      {/* PEDIDO */}
      <div className="col-order">
        <span className="order-id">{shortId(order.id)}</span>
        <span className="order-time">{formatTime(order.createdAt)}</span>
        {order.paymentStatus && (
          <span
            className="order-payment-status"
            style={{ color: PAYMENT_STATUS_COLOR[order.paymentStatus] }}
          >
            <span className="order-payment-label">Pago: </span>{PAYMENT_STATUS_LABEL[order.paymentStatus] ?? order.paymentStatus}
          </span>
        )}
      </div>

      {/* CLIENTE */}
      <div className="col-customer">
        <div className="customer-avatar" aria-hidden="true">
          {getInitials(order.customerName)}
        </div>
        <div className="customer-info">
          <span className="customer-name">{order.customerName ?? '—'}</span>
          {order.customerPhone && (
            <span className="customer-phone">
              <PhoneIcon />
              {order.customerPhone}
            </span>
          )}
          {order.orderType === 'DELIVERY' && order.deliveryAddress && (
            <span className="customer-address">
              <PinIcon />
              {order.deliveryAddress}
            </span>
          )}
        </div>
      </div>

      {/* PRODUCTOS */}
      <div className="col-items">
        {order.items?.slice(0, 2).map((item, i) => (
          <span key={i} className="item-line">
            <span className="item-qty">{item.quantity}×</span>
            {' '}{item.productName}
          </span>
        ))}
        {order.items?.length > 2 && (
          <span className="item-more">+{order.items.length - 2} más</span>
        )}
      </div>

      {/* NOTAS */}
      <div className="col-notes">
        {order.notes
          ? <span className="notes-text">"{order.notes}"</span>
          : <span className="notes-empty">—</span>
        }
      </div>

      {/* ESTADO */}
      <div className="col-status">
        <span
          className="status-badge"
          style={{ backgroundColor: cfg.bg, color: cfg.color, borderColor: cfg.border }}
        >
          <span className="status-dot" style={{ backgroundColor: cfg.color }} />
          {cfg.label ?? order.status}
        </span>
        <span className="order-type-label">
          {order.orderType === 'DELIVERY'
            ? <><ScooterIcon /> Delivery</>
            : <><StoreIcon /> Retiro en local</>}
        </span>
      </div>

      {/* ACCIÓN */}
      <div className="col-action">
        {isCancelled ? (
          <button
            className="action-dismiss-btn"
            type="button"
            onClick={onDismiss}
            aria-label="Descartar pedido cancelado"
          >
            <TrashIcon />
          </button>
        ) : next ? (
          <button
            className="action-advance-btn"
            type="button"
            onClick={onAdvance}
          >
            {advancing === order.id ? '···' : 'Avanzar →'}
          </button>
        ) : (
          <span className="action-none">—</span>
        )}
      </div>

    </div>
  )
}
