// US-08-04 / US-08-09: validaciones al cerrar turno desde la página RESUMEN.
// El botón "Cerrar turno" vive en /shifts/summary (visible solo con turno
// activo). El backend responde 422 cuando hay recepción activa o pedidos sin
// resolver —el mensaje se muestra dentro del modal de confirmación y el cierre
// no procede— y 200 con el resumen cuando el cierre procede (el turno pasa a
// inactivo y el botón de cierre desaparece).

import { test, expect } from './hermetic.js'

const OPEN_SHIFT = {
  active: true,
  shiftId: 'shift-0000-0000-0000-000000000001',
  openedAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
  openedBy: 'Encargado Demo',
}

const LIVE_SUMMARY = {
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

// Abre /shifts/summary con turno activo, dispara el modal de cierre y confirma.
async function startCloseFlow(page) {
  await page.goto('/shifts/summary')
  await expect(page.locator('.summary-page')).toBeVisible({ timeout: 5_000 })

  // Con turno abierto, la página de Resumen muestra el botón "Cerrar turno".
  const closeBtn = page.locator('.summary-close-btn')
  await expect(closeBtn).toBeVisible({ timeout: 5_000 })
  await closeBtn.click()

  // Modal de confirmación → confirmar el cierre (botón danger).
  await expect(page.locator('.summary-modal')).toBeVisible({ timeout: 3_000 })
  await page.locator('.summary-modal-btn--danger').click()
}

test.describe('US-08 · validaciones de cierre de turno', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript((token) => {
      localStorage.clear()
      sessionStorage.clear()
      localStorage.setItem('pedisur_token', token)
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
    // Resumen en vivo del turno activo (RESUMEN lo consume al montar).
    await page.route('**/backoffice/shifts/current/summary', route =>
      route.fulfill({ json: LIVE_SUMMARY })
    )
    // Sin turno activo (post-cierre), RESUMEN consulta el historial.
    await page.route('**/backoffice/shifts/history**', route =>
      route.fulfill({ json: { content: [] } })
    )
    await page.route('**/branches/**', route =>
      route.fulfill({ json: { id: 1, name: 'Puerto Madryn', acceptingOrders: true } })
    )
    // ConfigProvider (rol MANAGER/ADMIN) precarga el catálogo del tenant al montar.
    await page.route('**/backoffice/categories**', route =>
      route.fulfill({ json: [] })
    )
    await page.route('**/backoffice/products**', route =>
      route.fulfill({ json: [] })
    )
    // El glob con ** final captura también la variante con query (?shiftId=...),
    // que la app pide al haber un turno activo.
    await page.route('**/backoffice/orders**', route => {
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

    // El mensaje del backend se muestra dentro del modal y el cierre no procede.
    await expect(page.locator('.summary-modal-error'))
      .toContainText('Desactivá la recepción de pedidos antes de cerrar el turno', { timeout: 5_000 })
    // El turno sigue activo: el botón de cierre permanece disponible.
    await expect(page.locator('.summary-close-btn')).toBeVisible()
  })

  test('bloquea el cierre cuando existen pedidos activos sin resolver', async ({ page }) => {
    await page.route('**/backoffice/shifts/close', route =>
      route.fulfill({
        status: 422,
        json: { status: 422, message: 'Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.' },
      })
    )

    await startCloseFlow(page)

    await expect(page.locator('.summary-modal-error'))
      .toContainText('Hay pedidos activos sin resolver. Resolválos antes de cerrar el turno.', { timeout: 5_000 })
    await expect(page.locator('.summary-close-btn')).toBeVisible()
  })

  test('permite cerrar el turno cuando se cumplen las condiciones', async ({ page }) => {
    let shiftClosed = false
    // Tras el cierre, el turno pasa a inactivo.
    await page.route('**/backoffice/shifts/current', route =>
      route.fulfill({ json: shiftClosed ? { active: false } : OPEN_SHIFT })
    )
    await page.route('**/backoffice/shifts/close', route => {
      shiftClosed = true
      route.fulfill({ json: LIVE_SUMMARY })
    })

    await startCloseFlow(page)

    // Cierre exitoso: el modal de confirmación se cierra y, al quedar el turno
    // inactivo, el botón "Cerrar turno" desaparece de la página.
    await expect(page.locator('.summary-modal')).toHaveCount(0, { timeout: 5_000 })
    await expect(page.locator('.summary-close-btn')).toHaveCount(0, { timeout: 5_000 })
  })
})
