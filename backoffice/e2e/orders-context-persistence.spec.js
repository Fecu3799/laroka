// El estado de la lista de pedidos vive en OrdersProvider (Layout) y persiste
// entre navegaciones: ir a /summary y volver a /orders NO debe re-fetchear la
// lista (Orders se desmonta/remonta pero el provider permanece montado).

import { test, expect } from '@playwright/test'

const ORDER_UUID = 'aabbccdd-3333-0000-0000-000000000003'

function makeDemoToken() {
  const payload = Buffer.from(
    JSON.stringify({
      sub: 'staff@laroka.com',
      branchId: 1,
      branchName: 'Puerto Madryn',
      role: 'STAFF',
      exp: 9999999999,
    })
  ).toString('base64')
  return `eyJhbGciOiJIUzI1NiJ9.${payload}.demo-sig`
}

const DEMO_TOKEN = makeDemoToken()

const DEMO_ORDER = {
  id: ORDER_UUID,
  status: 'RECEIVED',
  orderType: 'DELIVERY',
  customerName: 'Cliente Persistente',
  customerPhone: '2804000003',
  deliveryAddress: 'Av. Roca 789',
  items: [{ productName: 'Muzzarella', quantity: 1, unitPrice: 2500 }],
  subtotal: 2500,
  deliveryFee: 500,
  serviceFee: 100,
  totalAmount: 3100,
  paymentMethod: 'CASH',
  paymentStatus: 'PENDING',
  notes: null,
  createdAt: new Date().toISOString(),
  statusHistory: [],
  origin: 'CLIENT',
}

test.describe('OrdersContext · persistencia de la lista al navegar', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.clear()
      sessionStorage.clear()
      localStorage.setItem('laroka_token', token)
    }, DEMO_TOKEN)

    await page.route('**/actuator/health', route =>
      route.fulfill({ json: { status: 'UP' } })
    )
    await page.route('**/backoffice/events', route =>
      route.fulfill({ status: 200, body: '' })
    )
    await page.route('**/backoffice/shifts/current', route =>
      route.fulfill({ json: { active: false } })
    )
    await page.route('**/branches/**', route =>
      route.fulfill({ json: { id: 1, name: 'Puerto Madryn', acceptingOrders: false } })
    )
    // La vista /summary, sin turno activo, consulta el historial.
    await page.route('**/backoffice/shifts/history**', route =>
      route.fulfill({ json: { content: [] } })
    )
    await page.route('**/backoffice/orders', route => {
      if (route.request().method() === 'GET') {
        route.fulfill({ json: [DEMO_ORDER] })
      } else {
        route.continue()
      }
    })
  })

  test('la lista persiste al navegar a /summary y volver sin re-fetchear', async ({ page }) => {
    // Contar SOLO los GET a la lista (no el detalle /backoffice/orders/{id}).
    let listGets = 0
    page.on('request', req => {
      if (req.method() === 'GET' && new URL(req.url()).pathname.endsWith('/backoffice/orders')) {
        listGets++
      }
    })

    await page.goto('/orders')
    await expect(page.locator('.orders-page')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.orders-row')).toHaveCount(1, { timeout: 5_000 })

    // La lista ya cargó: registramos cuántos GET de lista ocurrieron.
    const getsAfterInitialLoad = listGets
    expect(getsAfterInitialLoad).toBeGreaterThanOrEqual(1)

    // ── Navegar a /summary (navegación in-app, Layout permanece montado) ──
    await page.getByRole('link', { name: 'RESUMEN' }).click()
    await expect(page).toHaveURL('/summary')
    await expect(page.locator('.summary-page')).toBeVisible({ timeout: 5_000 })

    // ── Volver a /orders ─────────────────────────────────────────
    await page.getByRole('link', { name: 'PEDIDOS' }).click()
    await expect(page).toHaveURL('/orders')

    // La lista sigue visible con el mismo pedido, sin spinner de carga.
    await expect(page.locator('.orders-row')).toHaveCount(1, { timeout: 5_000 })
    await expect(page.getByText('Cliente Persistente')).toBeVisible()

    // Margen para detectar cualquier request tardío de la lista.
    await page.waitForTimeout(500)

    // No se disparó ningún GET nuevo de la lista al volver.
    expect(listGets).toBe(getsAfterInitialLoad)
  })
})
