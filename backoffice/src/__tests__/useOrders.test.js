import { renderHook, act, waitFor } from '@testing-library/react'
import useOrders from '../hooks/useOrders'

const ORDER_A = {
  id: 'order-a',
  status: 'RECEIVED',
  paymentStatus: 'PENDING',
  customerName: 'Juan',
  items: [],
}

const ORDER_B = {
  id: 'order-b',
  status: 'IN_PREPARATION',
  paymentStatus: 'APPROVED',
  customerName: 'María',
  items: [],
}

beforeEach(() => {
  sessionStorage.clear()
  global.fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => [],
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

// ── Criterion 7: dismissedIds no se limpia al llamar refresh() ──

test('dismissedIds persiste después de refresh()', async () => {
  const { result } = renderHook(() => useOrders('test-token'))

  // Esperar a que cargue el estado inicial
  await waitFor(() => expect(result.current.loading).toBe(false))

  // Descartar un pedido
  act(() => { result.current.dismissOrder('order-abc') })
  expect(result.current.dismissedIds.has('order-abc')).toBe(true)

  // Llamar refresh()
  act(() => { result.current.refresh() })

  // Esperar a que el fetch del refresh se complete
  await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2))
  await waitFor(() => expect(result.current.loading).toBe(false))

  // dismissedIds debe seguir teniendo el id descartado
  expect(result.current.dismissedIds.has('order-abc')).toBe(true)
})

test('dismissedIds acumula múltiples ids y todos persisten tras refresh()', async () => {
  const { result } = renderHook(() => useOrders('test-token'))
  await waitFor(() => expect(result.current.loading).toBe(false))

  act(() => {
    result.current.dismissOrder('order-1')
    result.current.dismissOrder('order-2')
    result.current.dismissOrder('order-3')
  })

  expect(result.current.dismissedIds.size).toBe(3)

  act(() => { result.current.refresh() })
  await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2))
  await waitFor(() => expect(result.current.loading).toBe(false))

  expect(result.current.dismissedIds.has('order-1')).toBe(true)
  expect(result.current.dismissedIds.has('order-2')).toBe(true)
  expect(result.current.dismissedIds.has('order-3')).toBe(true)
})

test('dismissedIds se inicializa desde sessionStorage', async () => {
  sessionStorage.setItem('laroka_dismissed_ids', JSON.stringify(['stored-order']))

  const { result } = renderHook(() => useOrders('test-token'))
  await waitFor(() => expect(result.current.loading).toBe(false))

  expect(result.current.dismissedIds.has('stored-order')).toBe(true)
})

// ── replaceOrderInList ────────────────────────────────────────────

describe('replaceOrderInList', () => {
  test('actualiza el item correcto sin modificar los demás', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [ORDER_A, ORDER_B],
    })

    const { result } = renderHook(() => useOrders('test-token'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.orders).toHaveLength(2)

    const updatedA = { ...ORDER_A, status: 'IN_PREPARATION' }
    act(() => { result.current.replaceOrderInList(updatedA) })

    expect(result.current.orders[0].status).toBe('IN_PREPARATION')
    expect(result.current.orders[1]).toEqual(ORDER_B)
  })

  test('agrega el pedido al inicio si no existe en la lista', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [ORDER_A],
    })

    const { result } = renderHook(() => useOrders('test-token'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    const newOrder = { id: 'order-new', status: 'RECEIVED', customerName: 'Carlos', items: [] }
    act(() => { result.current.replaceOrderInList(newOrder) })

    expect(result.current.orders).toHaveLength(2)
    expect(result.current.orders[0].id).toBe('order-new')
    expect(result.current.orders[1]).toEqual(ORDER_A)
  })
})
