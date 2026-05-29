// US-06-F-05 (test 2): flujo de cancelación APPROVE y REJECT
// desde un pedido con status CANCELLATION_REQUESTED.
//
// El token se inyecta directamente en localStorage para omitir el login
// y centrarse en el flujo de cancelación.

import { test, expect } from '@playwright/test'

const ORDER_UUID = 'ccddee00-2222-0000-0000-000000000002'

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
    customerName: 'Cliente Cancel',
    customerPhone: '2804111111',
    deliveryAddress: 'San Martín 456',
    items: [{ productName: 'Especial', quantity: 2, unitPrice: 3000 }],
    subtotal: 6000,
    deliveryFee: 500,
    serviceFee: 100,
    totalAmount: 6600,
    paymentMethod: 'CASH',
    paymentStatus: 'PENDING',
    notes: null,
    createdAt: new Date().toISOString(),
    statusHistory: [],
    origin: 'CLIENT',
  }
}

test.describe('US-06-F-05 · resolución de solicitud de cancelación', () => {
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
  })

  test('APPROVE: la solicitud de cancelación se aprueba y el pedido queda CANCELLED', async ({ page }) => {
    let currentOrder = makeOrder('CANCELLATION_REQUESTED')

    await page.route(`**/backoffice/orders/${ORDER_UUID}`, route =>
      route.fulfill({ json: currentOrder })
    )
    await page.route(`**/backoffice/orders/${ORDER_UUID}/cancel-request`, route => {
      const body = route.request().postDataJSON()
      if (body?.action === 'APPROVE') {
        currentOrder = makeOrder('CANCELLED')
      }
      route.fulfill({ status: 200, body: '' })
    })
    await page.route('**/backoffice/orders', route => {
      if (route.request().method() === 'GET') {
        route.fulfill({ json: [currentOrder] })
      } else {
        route.continue()
      }
    })

    await page.goto('/orders')
    await expect(page.locator('.orders-page')).toBeVisible({ timeout: 5_000 })

    // Abrir detalle del pedido en CANCELLATION_REQUESTED
    await page.locator('.orders-row').first().click()
    await expect(page.locator('.orders-detail-col')).toBeVisible({ timeout: 3_000 })

    // Verificar banner de solicitud de cancelación
    await expect(page.locator('.detail-cancel-request-banner')).toBeVisible()
    await expect(page.locator('.detail-cancel-request-title')).toContainText('Solicitud de cancelación')

    // Aprobar cancelación
    await page.getByRole('button', { name: /Aprobar cancelación/ }).click()

    // El estado del pedido pasa a CANCELLED
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('Cancelado', { timeout: 5_000 })
  })

  test('REJECT: la solicitud de cancelación se rechaza y el pedido vuelve a IN_PREPARATION', async ({ page }) => {
    let currentOrder = makeOrder('CANCELLATION_REQUESTED')

    await page.route(`**/backoffice/orders/${ORDER_UUID}`, route =>
      route.fulfill({ json: currentOrder })
    )
    await page.route(`**/backoffice/orders/${ORDER_UUID}/cancel-request`, route => {
      const body = route.request().postDataJSON()
      if (body?.action === 'REJECT') {
        currentOrder = makeOrder('IN_PREPARATION')
      }
      route.fulfill({ status: 200, body: '' })
    })
    await page.route('**/backoffice/orders', route => {
      if (route.request().method() === 'GET') {
        route.fulfill({ json: [currentOrder] })
      } else {
        route.continue()
      }
    })

    await page.goto('/orders')
    await expect(page.locator('.orders-page')).toBeVisible({ timeout: 5_000 })

    await page.locator('.orders-row').first().click()
    await expect(page.locator('.orders-detail-col')).toBeVisible({ timeout: 3_000 })

    await expect(page.locator('.detail-cancel-request-banner')).toBeVisible()

    // Rechazar cancelación
    await page.getByRole('button', { name: /Rechazar cancelación/ }).click()

    // El pedido vuelve a EN_PREPARATION y aparece el botón de avance
    await expect(
      page.locator('.orders-detail-col .status-badge').first()
    ).toContainText('En preparación', { timeout: 5_000 })
    await expect(page.locator('.detail-action-advance')).toContainText('En camino', { timeout: 3_000 })
  })
})
