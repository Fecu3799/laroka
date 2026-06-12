// US-08-04 / US-08-09: validaciones al cerrar turno desde el SubHeader.
// El backend responde 422 cuando hay recepción activa o pedidos sin resolver,
// y 200 con el resumen cuando el cierre procede. Acá se mockea cada respuesta
// y se verifica el feedback en la UI (toast de error / modal de resumen).

import { test, expect } from '@playwright/test'

const OPEN_SHIFT = {
  active: true,
  shiftId: 'shift-0000-0000-0000-000000000001',
  openedAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
  openedBy: 'Encargado Demo',
}

function makeDemoToken() {
  const payload = Buffer.from(
    JSON.stringify({
      sub: 'manager@laroka.com',
      branchId: 1,
      branchName: 'Puerto Madryn',
      role: 'MANAGER',
      exp: 9999999999,
    })
  ).toString('base64')
  return `eyJhbGciOiJIUzI1NiJ9.${payload}.demo-sig`
}

const DEMO_TOKEN = makeDemoToken()

async function startCloseFlow(page) {
  await page.goto('/orders')
  await expect(page.locator('.orders-page')).toBeVisible({ timeout: 5_000 })

  // Con turno abierto, el SubHeader muestra el botón "Cerrar turno".
  const closeBtn = page.getByRole('button', { name: 'Cerrar turno' })
  await expect(closeBtn).toBeVisible({ timeout: 5_000 })
  await closeBtn.click()

  // Modal de confirmación → confirmar el cierre.
  await page.getByRole('button', { name: 'Confirmar cierre' }).click()
}

test.describe('US-08 · validaciones de cierre de turno', () => {
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
      route.fulfill({ json: OPEN_SHIFT })
    )
    await page.route('**/branches/**', route =>
      route.fulfill({ json: { id: 1, name: 'Puerto Madryn', acceptingOrders: true } })
    )
    await page.route('**/backoffice/orders', route => {
      if (route.request().method() === 'GET') {
        route.fulfill({ json: [] })
      } else {
        route.continue()
      }
    })
  })

  test('bloquea el cierre cuando la recepción de pedidos está activa', async ({ page }) => {
    await page.route('**/backoffice/shifts/close', route =>
      route.fulfill({
        status: 422,
        json: { status: 422, message: 'Desactivá la recepción de pedidos antes de cerrar el turno' },
      })
    )

    await startCloseFlow(page)

    await expect(
      page.getByText('Desactivá la recepción de pedidos antes de cerrar el turno')
    ).toBeVisible({ timeout: 5_000 })
    // El cierre no procede: no aparece el modal de resumen.
    await expect(page.getByText('Resumen del turno')).toHaveCount(0)
  })

  test('bloquea el cierre cuando existen pedidos activos sin resolver', async ({ page }) => {
    await page.route('**/backoffice/shifts/close', route =>
      route.fulfill({
        status: 422,
        json: { status: 422, message: 'Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.' },
      })
    )

    await startCloseFlow(page)

    await expect(
      page.getByText('Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.')
    ).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('Resumen del turno')).toHaveCount(0)
  })

  test('permite cerrar el turno cuando se cumplen las condiciones y muestra el resumen', async ({ page }) => {
    await page.route('**/backoffice/shifts/close', route =>
      route.fulfill({
        json: {
          shiftId: OPEN_SHIFT.shiftId,
          totalOrders: 5,
          deliveredOrders: 4,
          cancelledOrders: 1,
          totalRevenue: 12000,
          cashRevenue: 8000,
          mpRevenue: 4000,
          qrRevenue: 0,
          averageTicket: 3000,
          deliveryOrders: 3,
          takeawayOrders: 1,
          cancellationRate: 20,
          calculatedAt: new Date().toISOString(),
        },
      })
    )

    await startCloseFlow(page)

    // El modal de resumen del turno aparece tras el cierre exitoso.
    await expect(page.getByText('Resumen del turno')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('Total recaudado')).toBeVisible()
  })
})
