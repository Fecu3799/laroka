// US-06-F-04: flujo completo seleccionar sucursal → menú → carrito → checkout
// efectivo → confirmación → pantalla de seguimiento con status RECEIVED.
//
// Todos los llamados a la API se interceptan con datos de demo;
// no se requiere backend en ejecución.

import { test, expect } from '@playwright/test'

const BRANCH_ID = 1
const ORDER_ID = 'demo-order-0000-0000-0000-000000000001'

const DEMO_BRANCHES = [
  {
    id: BRANCH_ID,
    name: 'Puerto Madryn',
    deliveryFee: 500,
    serviceFee: 100,
    phone: '2804000000',
    estimatedDeliveryMinutes: 30,
  },
]

const DEMO_MENU = [
  {
    categoryId: 1,
    categoryName: 'Pizzas',
    products: [
      { id: 101, name: 'Muzzarella', description: 'Clásica', price: 2500, imageUrl: null },
    ],
  },
  { categoryId: 3, categoryName: 'Bebidas', products: [] },
]

const DEMO_ORDER_STATUS = {
  orderId: ORDER_ID,
  status: 'RECEIVED',
  orderType: 'DELIVERY',
  subtotal: 2500,
  deliveryFee: 500,
  serviceFee: 100,
  totalAmount: 3100,
  history: [],
}

// ── Helpers compartidos ───────────────────────────────────────

const CANCEL_ORDER_ID = 'cancel-order-0000-0000-0000-000000000099'

const STORED_BRANCH = JSON.stringify({
  id: BRANCH_ID,
  name: 'Puerto Madryn',
  deliveryFee: 500,
  serviceFee: 100,
  phone: '2804000000',
  estimatedDeliveryMinutes: 30,
})

function makeStatusResponse(status, cancellationReason = null, cancelledByStaff = false) {
  const history = (cancellationReason || cancelledByStaff)
    ? [{
        toStatus: status,
        cancellationReason,
        cancelledByStaff,
        changedAt: new Date().toISOString(),
      }]
    : []
  return {
    status,
    orderType: 'DELIVERY',
    subtotal: 2500,
    deliveryFee: 500,
    serviceFee: 100,
    totalAmount: 3100,
    deliveryAddress: 'Av. Roca 123',
    history,
  }
}

// ── Checkout original ─────────────────────────────────────────

test.describe('US-06-F-04 · checkout en efectivo con seguimiento', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.clear()
      sessionStorage.clear()
    })

    // Mocks más específicos primero (orden importa)
    await page.route(`**/${ORDER_ID}/status`, route =>
      route.fulfill({ json: DEMO_ORDER_STATUS })
    )
    await page.route(`**/${ORDER_ID}/items`, route =>
      route.fulfill({ json: [{ name: 'Muzzarella', quantity: 1, subtotal: 2500 }] })
    )
    await page.route(`**/branches/${BRANCH_ID}/menu`, route =>
      route.fulfill({ json: DEMO_MENU })
    )
    // El checkout (US-08-10) verifica acceptingOrders del branch antes de confirmar.
    await page.route(`**/branches/${BRANCH_ID}`, route =>
      route.fulfill({ json: { ...DEMO_BRANCHES[0], acceptingOrders: true } })
    )
    await page.route(/\/branches(\?.*)?$/, route =>
      route.fulfill({ json: DEMO_BRANCHES })
    )
    await page.route('**/orders', route => {
      if (route.request().method() === 'POST') {
        route.fulfill({ json: { orderId: ORDER_ID } })
      } else {
        route.continue()
      }
    })
  })

  test('seleccionar sucursal → agregar producto → checkout → confirmación → RECEIVED', async ({ page }) => {
    await page.goto('/')

    // SplashScreen: espera la carga de /branches + 1.5 s de animación.
    // Luego muestra BranchSelection.
    const branchBtn = page.getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })
    await expect(branchBtn).toBeVisible({ timeout: 10_000 })

    await branchBtn.click()

    // MenuScreen carga el menú de la sucursal seleccionada
    const addBtn = page.getByRole('button', { name: 'Agregar Muzzarella', exact: true })
    await expect(addBtn).toBeVisible({ timeout: 5_000 })
    await addBtn.click()

    // Navegar a la pestaña Carrito
    await page.getByRole('button', { name: 'Cart' }).click()
    await expect(page.getByText('Muzzarella')).toBeVisible()

    // Ir a checkout
    await page.getByRole('button', { name: /IR A PAGAR/ }).click()

    // Completar datos del cliente
    await page.getByPlaceholder('¿Cómo te llamás?').fill('Cliente Demo')
    await page.getByPlaceholder('11 0000-0000').fill('2804000000')
    await page.getByPlaceholder('Calle y número, piso, depto').fill('Av. Roca 123')

    // Efectivo debe estar seleccionado por defecto
    const efectivoBtn = page.getByRole('button', { name: /Efectivo/ }).first()
    await expect(efectivoBtn).toHaveAttribute('aria-pressed', 'true')

    // Confirmar pedido
    await page.getByRole('button', { name: /CONFIRMAR PEDIDO/ }).click()

    // Pantalla de confirmación
    await expect(page.getByRole('heading', { name: '¡Pedido confirmado!' })).toBeVisible({ timeout: 5_000 })

    // Después de 3 s se vuelve al menú; el banner de seguimiento muestra RECEIVED
    await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 8_000 })
  })

  test('bloquea la confirmación cuando acceptingOrders es false (US-08-10)', async ({ page }) => {
    // El local dejó de aceptar pedidos: override del branch a acceptingOrders:false.
    await page.route(`**/branches/${BRANCH_ID}`, route =>
      route.fulfill({ json: { ...DEMO_BRANCHES[0], acceptingOrders: false } })
    )
    // Si la confirmación se bloquea correctamente, POST /orders nunca debe dispararse.
    let orderPosted = false
    page.on('request', req => {
      if (req.method() === 'POST' && new URL(req.url()).pathname.endsWith('/orders')) {
        orderPosted = true
      }
    })

    await page.goto('/')

    const branchBtn = page.getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })
    await expect(branchBtn).toBeVisible({ timeout: 10_000 })
    await branchBtn.click()

    const addBtn = page.getByRole('button', { name: 'Agregar Muzzarella', exact: true })
    await expect(addBtn).toBeVisible({ timeout: 5_000 })
    await addBtn.click()

    await page.getByRole('button', { name: 'Cart' }).click()
    await page.getByRole('button', { name: /IR A PAGAR/ }).click()

    await page.getByPlaceholder('¿Cómo te llamás?').fill('Cliente Demo')
    await page.getByPlaceholder('11 0000-0000').fill('2804000000')
    await page.getByPlaceholder('Calle y número, piso, depto').fill('Av. Roca 123')

    await page.getByRole('button', { name: /CONFIRMAR PEDIDO/ }).click()

    // Aparece el mensaje de bloqueo y NO se navega a la confirmación.
    await expect(
      page.getByText('El local no está aceptando pedidos en este momento')
    ).toBeVisible({ timeout: 5_000 })
    await expect(
      page.getByRole('heading', { name: '¡Pedido confirmado!' })
    ).toHaveCount(0)
    expect(orderPosted).toBe(false)
  })
})

// ── Cancelación desde el banner ───────────────────────────────

test.describe('US-06-F · cancelación de pedido desde el banner', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(({ orderId, storedBranch }) => {
      localStorage.clear()
      sessionStorage.clear()
      localStorage.setItem('laroka_preferred_branch', storedBranch)
      localStorage.setItem('laroka_active_orders', JSON.stringify([
        { orderId, branchId: 1 },
      ]))
    }, { orderId: CANCEL_ORDER_ID, storedBranch: STORED_BRANCH })

    await page.route(/\/branches(\?.*)?$/, route =>
      route.fulfill({ json: DEMO_BRANCHES })
    )
    await page.route(`**/branches/${BRANCH_ID}/menu`, route =>
      route.fulfill({ json: [] })
    )
  })

  test('cancelación directa sin motivo: POST enviado con reason null, banner muestra CANCELADO', async ({ page }) => {
    let capturedBody = null

    await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
      route.fulfill({ json: makeStatusResponse('RECEIVED') })
    )
    await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
      capturedBody = route.request().postDataJSON()
      route.fulfill({ status: 204 })
    })

    await page.goto('/')
    await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: 'Ver detalle' }).click()
    await page.getByRole('button', { name: 'Cancelar pedido' }).click()

    // Textarea visible; reason es opcional, confirmar habilitado sin texto
    await expect(page.locator('textarea')).toBeVisible()
    const confirmBtn = page.getByRole('button', { name: 'Confirmar cancelación' })
    await expect(confirmBtn).not.toBeDisabled()

    await confirmBtn.click()

    // Banner muestra CANCELADO
    await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 5_000 })
    // El body enviado no tiene reason
    expect(capturedBody?.reason).toBeFalsy()
  })

  test('cancelación directa con motivo: reason enviado, banner muestra Entendido (cancelación de cliente)', async ({ page }) => {
    const REASON = 'cambio de planes'
    let capturedBody = null

    await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
      route.fulfill({ json: makeStatusResponse('RECEIVED') })
    )
    await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
      capturedBody = route.request().postDataJSON()
      route.fulfill({ status: 204 })
    })

    await page.goto('/')
    await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: 'Ver detalle' }).click()
    await page.getByRole('button', { name: 'Cancelar pedido' }).click()
    await page.locator('textarea').fill(REASON)
    await page.getByRole('button', { name: 'Confirmar cancelación' }).click()

    // El reason fue enviado
    expect(capturedBody?.reason).toBe(REASON)

    // Banner muestra CANCELADO con botón "Entendido" (cancelación iniciada por cliente)
    await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 5_000 })
    await expect(page.getByRole('button', { name: 'Entendido' })).toBeVisible()

    // Entendido descarta el pedido del banner
    await page.getByRole('button', { name: 'Entendido' }).click()
    await expect(page.locator('[data-status="CANCELLED"]')).not.toBeVisible({ timeout: 3_000 })
  })

  test('solicitud de cancelación: Confirmar deshabilitado sin texto, habilitado al escribir', async ({ page }) => {
    await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
      route.fulfill({ json: makeStatusResponse('IN_PREPARATION') })
    )
    await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route =>
      route.fulfill({ status: 204 })
    )

    await page.goto('/')
    await expect(page.locator('[data-status="IN_PREPARATION"]')).toBeVisible({ timeout: 10_000 })

    await page.getByRole('button', { name: 'Ver detalle' }).click()
    await page.getByRole('button', { name: 'Solicitar cancelación' }).click()

    // Confirmar deshabilitado sin texto (motivo obligatorio para CANCELLATION_REQUESTED)
    const confirmBtn = page.getByRole('button', { name: 'Confirmar solicitud' })
    await expect(confirmBtn).toBeDisabled()

    // Al ingresar texto se habilita
    await page.locator('textarea').fill('ya no quiero el pedido')
    await expect(confirmBtn).not.toBeDisabled()

    await confirmBtn.click()

    // Banner refleja CANCELLATION_REQUESTED
    await expect(page.locator('[data-status="CANCELLATION_REQUESTED"]')).toBeVisible({ timeout: 5_000 })
  })

  test('banner con pedido CANCELLED por operador: modal muestra el motivo y Cerrar lo descarta', async ({ page }) => {
    const REASON = 'sin stock disponible'

    await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
      route.fulfill({ json: makeStatusResponse('CANCELLED', REASON, true) })
    )

    await page.goto('/')
    await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 10_000 })

    // Botón "Ver motivo de cancelación" visible en el banner
    const viewBtn = page.getByRole('button', { name: 'Ver motivo de cancelación' })
    await expect(viewBtn).toBeVisible()
    await viewBtn.click()

    // Modal con el motivo
    await expect(page.getByText(REASON)).toBeVisible()

    // Cerrar descarta el pedido del banner
    await page.getByRole('button', { name: 'Cerrar' }).click()
    await expect(page.locator('[data-status="CANCELLED"]')).not.toBeVisible({ timeout: 3_000 })
  })
})
