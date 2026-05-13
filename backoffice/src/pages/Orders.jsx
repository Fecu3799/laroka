import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuth from '../hooks/useAuth'
import useOrders from '../hooks/useOrders'
import { advanceOrderStatus } from '../services/ordersService'
import './Orders.css'

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

export default function Orders() {
  const navigate = useNavigate()
  const { branchId } = useAuth()
  const orders = useOrders(branchId)
  const [activeTab,   setActiveTab]   = useState('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedId,  setSelectedId]  = useState(null)
  const [advancing,   setAdvancing]   = useState(null)

  return (
    <div className="orders-page">

      {/* ── Top bar ──────────────────────────────────────────── */}
      <div className="orders-header">

        <div className="orders-header-title-block">
          <h1 className="orders-title">Pedidos</h1>
          <span className="orders-subtitle">0 pedidos totales · 0 activos</span>
        </div>

        <div className="orders-header-spacer" />

        <div className="orders-chips">
          {STATUS_CHIPS.map(chip => (
            <div
              key={chip.key}
              className="orders-chip"
              style={{ backgroundColor: chip.bg, color: chip.color, borderColor: chip.border }}
            >
              <span className="orders-chip-count">0</span>
              <span className="orders-chip-label">{chip.label}</span>
            </div>
          ))}
        </div>

        <div className="orders-search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="8" stroke="#7a9b80" strokeWidth="2" />
            <path d="m21 21-4.35-4.35" stroke="#7a9b80" strokeWidth="2" strokeLinecap="round" />
          </svg>
          <input
            className="orders-search-input"
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Buscar #ID o cliente..."
          />
        </div>

        <button className="orders-refresh-btn" type="button">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M1 4v6h6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M3.51 15a9 9 0 1 0 .49-4.95" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
          </svg>
          Actualizar lista
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
              0
            </span>
          </button>
        ))}
      </div>

      {/* ── Main area ────────────────────────────────────────── */}
      <div className="orders-layout">
        <div className="orders-layout-placeholder">
          Lista de pedidos — próximo paso
        </div>
      </div>

    </div>
  )
}
