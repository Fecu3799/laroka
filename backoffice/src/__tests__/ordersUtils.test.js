import {
  STATUS_CONFIG,
  sortOrders,
  getNextStatus,
  canGoBack,
  canCancel,
  canConfirmOrder,
} from '../utils/ordersUtils'

// ── Criterion 1: STATUS_CONFIG structure ──────────────────────

describe('STATUS_CONFIG', () => {
  const ALL_STATUSES = [
    'PENDING_PAYMENT',
    'RECEIVED',
    'IN_PREPARATION',
    'ON_THE_WAY',
    'READY_FOR_PICKUP',
    'DELIVERED',
    'CANCELLED',
  ]

  test.each(ALL_STATUSES)('%s tiene bg, color, border y label', (status) => {
    const cfg = STATUS_CONFIG[status]
    expect(cfg).toBeDefined()
    expect(cfg.bg).toMatch(/^#[0-9a-f]{6}$/i)
    expect(cfg.color).toMatch(/^#[0-9a-f]{6}$/i)
    expect(cfg.border).toMatch(/^#[0-9a-f]{6}$/i)
    expect(typeof cfg.label).toBe('string')
    expect(cfg.label.length).toBeGreaterThan(0)
  })
})

// ── Criterion 2: sortOrders ───────────────────────────────────

describe('sortOrders', () => {
  test('ordena por STATUS_PRIORITY ascendente', () => {
    const orders = [
      { id: '1', status: 'DELIVERED',      createdAt: '2024-01-01T10:00:00Z' },
      { id: '2', status: 'RECEIVED',       createdAt: '2024-01-01T10:00:00Z' },
      { id: '3', status: 'IN_PREPARATION', createdAt: '2024-01-01T10:00:00Z' },
    ]
    const sorted = sortOrders(orders)
    expect(sorted.map(o => o.status)).toEqual(['RECEIVED', 'IN_PREPARATION', 'DELIVERED'])
  })

  test('desempata por createdAt descendente — más reciente primero', () => {
    const orders = [
      { id: 'old',    status: 'RECEIVED', createdAt: '2024-01-01T08:00:00Z' },
      { id: 'newest', status: 'RECEIVED', createdAt: '2024-01-01T10:00:00Z' },
      { id: 'mid',    status: 'RECEIVED', createdAt: '2024-01-01T09:00:00Z' },
    ]
    const sorted = sortOrders(orders)
    expect(sorted.map(o => o.id)).toEqual(['newest', 'mid', 'old'])
  })

  test('no muta el array original', () => {
    const orders = [
      { id: '1', status: 'DELIVERED', createdAt: '2024-01-01T10:00:00Z' },
      { id: '2', status: 'RECEIVED',  createdAt: '2024-01-01T10:00:00Z' },
    ]
    const snapshot = [...orders]
    sortOrders(orders)
    expect(orders).toEqual(snapshot)
  })

  test('estados con misma prioridad mantienen orden interno por fecha', () => {
    const orders = [
      { id: 'a', status: 'ON_THE_WAY',     createdAt: '2024-01-01T09:00:00Z' },
      { id: 'b', status: 'READY_FOR_PICKUP', createdAt: '2024-01-01T11:00:00Z' },
    ]
    const sorted = sortOrders(orders)
    // Both have priority 3 — newer createdAt comes first
    expect(sorted[0].id).toBe('b')
    expect(sorted[1].id).toBe('a')
  })
})

// ── Criterion 4: getNextStatus ────────────────────────────────

describe('getNextStatus', () => {
  test.each([
    ['RECEIVED',       'DELIVERY',  'IN_PREPARATION'],
    ['RECEIVED',       'TAKEAWAY',  'IN_PREPARATION'],
    ['IN_PREPARATION', 'DELIVERY',  'ON_THE_WAY'],
    ['IN_PREPARATION', 'TAKEAWAY',  'READY_FOR_PICKUP'],
    ['ON_THE_WAY',     'DELIVERY',  'DELIVERED'],
    ['READY_FOR_PICKUP', 'TAKEAWAY', 'DELIVERED'],
  ])('%s + %s → %s', (status, orderType, expected) => {
    expect(getNextStatus(status, orderType)).toBe(expected)
  })

  test.each(['DELIVERED', 'CANCELLED', 'PENDING_PAYMENT'])(
    '%s → null',
    (status) => expect(getNextStatus(status, 'DELIVERY')).toBeNull()
  )
})

// ── Criterion 5: canGoBack ────────────────────────────────────

describe('canGoBack', () => {
  test.each(['IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP'])(
    '%s → true',
    (status) => expect(canGoBack(status)).toBe(true)
  )

  test.each(['RECEIVED', 'PENDING_PAYMENT', 'DELIVERED', 'CANCELLED'])(
    '%s → false',
    (status) => expect(canGoBack(status)).toBe(false)
  )
})

// ── Criterion 6: canCancel ────────────────────────────────────

describe('canCancel', () => {
  test('RECEIVED → true', () => {
    expect(canCancel('RECEIVED')).toBe(true)
  })

  test.each(['IN_PREPARATION', 'ON_THE_WAY', 'READY_FOR_PICKUP', 'DELIVERED', 'CANCELLED', 'PENDING_PAYMENT'])(
    '%s → false',
    (status) => expect(canCancel(status)).toBe(false)
  )
})

// ── Criterion 8: canConfirmOrder ──────────────────────────────

describe('canConfirmOrder', () => {
  test('deshabilitado sin items', () => {
    expect(canConfirmOrder({ cartItems: [], orderType: 'TAKEAWAY', deliveryAddress: '' })).toBe(false)
    expect(canConfirmOrder({ cartItems: [], orderType: 'DELIVERY', deliveryAddress: 'Calle 1' })).toBe(false)
  })

  test('deshabilitado si DELIVERY y deliveryAddress vacío o solo espacios', () => {
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'DELIVERY', deliveryAddress: '' })).toBe(false)
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'DELIVERY', deliveryAddress: '   ' })).toBe(false)
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'DELIVERY', deliveryAddress: null })).toBe(false)
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'DELIVERY', deliveryAddress: undefined })).toBe(false)
  })

  test('habilitado si DELIVERY con items y deliveryAddress válido', () => {
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'DELIVERY', deliveryAddress: 'Calle 123' })).toBe(true)
  })

  test('habilitado si TAKEAWAY con items (sin importar deliveryAddress)', () => {
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'TAKEAWAY', deliveryAddress: '' })).toBe(true)
    expect(canConfirmOrder({ cartItems: [{}], orderType: 'TAKEAWAY', deliveryAddress: null })).toBe(true)
  })
})
