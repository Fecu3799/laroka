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
    await page.route('**/branches', route =>
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
})
