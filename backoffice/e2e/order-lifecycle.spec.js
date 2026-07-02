// US-06-F-05 (test 1): login → ver pedido recibido → abrir detalle →
// avanzar estado RECEIVED → IN_PREPARATION → ON_THE_WAY → DELIVERED.
//
// Todos los llamados a la API se interceptan con datos de demo.

import { test, expect } from './hermetic.js'

const ORDER_UUID = 'aabbccdd-1111-0000-0000-000000000001'

// Genera un JWT mínimo que pasa la validación de decodeToken en useAuth.js
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

function makeOrder(status) {
  return {
    id: ORDER_UUID,
    status,
    orderType: 'DELIVERY',
    customerName: 'Cliente Demo',
    customerPhone: '2804000000',
    deliveryAddress: 'Av. Roca 123',
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
}

test.describe('US-06-F-05 · ciclo de vida del pedido', () => {
  test('login → ver pedido → RECEIVED → IN_PREPARATION → ON_THE_WAY → DELIVERED', async ({ page }) => {
    let currentOrder = makeOrder('RECEIVED')

    // Infraestructura del backoffice
    await page.route('**/actuator/health', route =>
      route.fulfill({ json: { status: 'UP' } })
    )
    await page.route('**/backoffice/events', route =>
      route.fulfill({ status: 200, body: '' })
    )

    // Hermético: turno y sucursal mockeados para no depender de un backend real.
    await page.route('**/backoffice/shifts/current', route =>
      route.fulfill({ json: { active: false } })
    )
    await page.route('**/backoffice/shifts/history**', route =>
      route.fulfill({ json: { content: [] } })
    )
    await page.route('**/branches/**', route =>
      route.fulfill({ json: { id: 1, name: 'Puerto Madryn', acceptingOrders: false } })
    )

    // Auth
    await page.route('**/auth/login', route =>
      route.fulfill({ json: { token: DEMO_TOKEN } })
    )

    // Detalle del pedido — siempre devuelve el estado actual
    await page.route(`**/backoffice/orders/${ORDER_UUID}`, route =>
      route.fulfill({ json: currentOrder })
    )

    // Avance de estado: actualiza el estado local antes de devolver 200
    await page.route(`**/backoffice/orders/${ORDER_UUID}/status`, route => {
      const body = route.request().postDataJSON()
      if (body?.nextStatus) {
        currentOrder = makeOrder(body.nextStatus)
      }
      route.fulfill({ status: 200, body: '' })
    })

    // Lista de pedidos — refleja el estado actual
    await page.route('**/backoffice/orders', route => {
      if (route.request().method() === 'GET') {
        route.fulfill({ json: [currentOrder] })
      } else {
        route.continue()
      }
    })

    // ── Login ────────────────────────────────────────────────────
    await page.goto('/login')
    await page.locator('#email').fill('staff@laroka.com')
    await page.locator('#password').fill('password123')
    await page.getByRole('button', { name: /INGRESAR AL PANEL/i }).click()

    await expect(page).toHaveURL('/orders', { timeout: 5_000 })
    await expect(page.locator('.orders-page')).toBeVisible()

    // ── Abrir detalle del pedido ─────────────────────────────────
    // shortId('aabbccdd-1111-0000-0000-000000000001') → #AABB
    await page.locator('.orders-row').first().click()
    await expect(page.locator('.orders-detail-col')).toBeVisible({ timeout: 3_000 })

    // Verificar que el estado inicial es RECEIVED
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('Recibido', { timeout: 3_000 })

    // ── RECEIVED → IN_PREPARATION ────────────────────────────────
    await page.locator('.detail-action-advance').click()
    await expect(page.locator('.detail-action-advance')).toContainText('En camino', { timeout: 5_000 })
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('En preparación', { timeout: 5_000 })

    // ── IN_PREPARATION → ON_THE_WAY ──────────────────────────────
    await page.locator('.detail-action-advance').click()
    await expect(page.locator('.detail-action-advance')).toContainText('Entregado', { timeout: 5_000 })
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('En camino', { timeout: 5_000 })

    // ── ON_THE_WAY → DELIVERED ───────────────────────────────────
    await page.locator('.detail-action-advance').click()
    await expect(page.locator('.detail-action-advance--done')).toContainText('Pedido finalizado', { timeout: 5_000 })
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('Entregado', { timeout: 5_000 })
  })
})
