# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: checkout-flow.spec.js >> US-06-F-04 · checkout en efectivo con seguimiento >> seleccionar sucursal → agregar producto → checkout → confirmación → RECEIVED
- Location: e2e/checkout-flow.spec.js:110:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })
Expected: visible
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })

```

```yaml
- status "Cargando La Roka":
  - img "La Roka Pizzería"
```

# Test source

```ts
  16  |     deliveryFee: 500,
  17  |     serviceFee: 100,
  18  |     phone: '2804000000',
  19  |     estimatedDeliveryMinutes: 30,
  20  |   },
  21  | ]
  22  | 
  23  | const DEMO_MENU = [
  24  |   {
  25  |     categoryId: 1,
  26  |     categoryName: 'Pizzas',
  27  |     products: [
  28  |       { id: 101, name: 'Muzzarella', description: 'Clásica', price: 2500, imageUrl: null },
  29  |     ],
  30  |   },
  31  |   { categoryId: 3, categoryName: 'Bebidas', products: [] },
  32  | ]
  33  | 
  34  | const DEMO_ORDER_STATUS = {
  35  |   orderId: ORDER_ID,
  36  |   status: 'RECEIVED',
  37  |   orderType: 'DELIVERY',
  38  |   subtotal: 2500,
  39  |   deliveryFee: 500,
  40  |   serviceFee: 100,
  41  |   totalAmount: 3100,
  42  |   history: [],
  43  | }
  44  | 
  45  | // ── Helpers compartidos ───────────────────────────────────────
  46  | 
  47  | const CANCEL_ORDER_ID = 'cancel-order-0000-0000-0000-000000000099'
  48  | 
  49  | const STORED_BRANCH = JSON.stringify({
  50  |   id: BRANCH_ID,
  51  |   name: 'Puerto Madryn',
  52  |   deliveryFee: 500,
  53  |   serviceFee: 100,
  54  |   phone: '2804000000',
  55  |   estimatedDeliveryMinutes: 30,
  56  | })
  57  | 
  58  | function makeStatusResponse(status, cancellationReason = null, cancelledByStaff = false) {
  59  |   const history = (cancellationReason || cancelledByStaff)
  60  |     ? [{
  61  |         toStatus: status,
  62  |         cancellationReason,
  63  |         cancelledByStaff,
  64  |         changedAt: new Date().toISOString(),
  65  |       }]
  66  |     : []
  67  |   return {
  68  |     status,
  69  |     orderType: 'DELIVERY',
  70  |     subtotal: 2500,
  71  |     deliveryFee: 500,
  72  |     serviceFee: 100,
  73  |     totalAmount: 3100,
  74  |     deliveryAddress: 'Av. Roca 123',
  75  |     history,
  76  |   }
  77  | }
  78  | 
  79  | // ── Checkout original ─────────────────────────────────────────
  80  | 
  81  | test.describe('US-06-F-04 · checkout en efectivo con seguimiento', () => {
  82  |   test.beforeEach(async ({ page }) => {
  83  |     await page.addInitScript(() => {
  84  |       localStorage.clear()
  85  |       sessionStorage.clear()
  86  |     })
  87  | 
  88  |     // Mocks más específicos primero (orden importa)
  89  |     await page.route(`**/${ORDER_ID}/status`, route =>
  90  |       route.fulfill({ json: DEMO_ORDER_STATUS })
  91  |     )
  92  |     await page.route(`**/${ORDER_ID}/items`, route =>
  93  |       route.fulfill({ json: [{ name: 'Muzzarella', quantity: 1, subtotal: 2500 }] })
  94  |     )
  95  |     await page.route(`**/branches/${BRANCH_ID}/menu`, route =>
  96  |       route.fulfill({ json: DEMO_MENU })
  97  |     )
  98  |     await page.route('**/branches', route =>
  99  |       route.fulfill({ json: DEMO_BRANCHES })
  100 |     )
  101 |     await page.route('**/orders', route => {
  102 |       if (route.request().method() === 'POST') {
  103 |         route.fulfill({ json: { orderId: ORDER_ID } })
  104 |       } else {
  105 |         route.continue()
  106 |       }
  107 |     })
  108 |   })
  109 | 
  110 |   test('seleccionar sucursal → agregar producto → checkout → confirmación → RECEIVED', async ({ page }) => {
  111 |     await page.goto('/')
  112 | 
  113 |     // SplashScreen: espera la carga de /branches + 1.5 s de animación.
  114 |     // Luego muestra BranchSelection.
  115 |     const branchBtn = page.getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })
> 116 |     await expect(branchBtn).toBeVisible({ timeout: 10_000 })
      |                             ^ Error: expect(locator).toBeVisible() failed
  117 | 
  118 |     await branchBtn.click()
  119 | 
  120 |     // MenuScreen carga el menú de la sucursal seleccionada
  121 |     const addBtn = page.getByRole('button', { name: 'Agregar Muzzarella', exact: true })
  122 |     await expect(addBtn).toBeVisible({ timeout: 5_000 })
  123 |     await addBtn.click()
  124 | 
  125 |     // Navegar a la pestaña Carrito
  126 |     await page.getByRole('button', { name: 'Cart' }).click()
  127 |     await expect(page.getByText('Muzzarella')).toBeVisible()
  128 | 
  129 |     // Ir a checkout
  130 |     await page.getByRole('button', { name: /IR A PAGAR/ }).click()
  131 | 
  132 |     // Completar datos del cliente
  133 |     await page.getByPlaceholder('¿Cómo te llamás?').fill('Cliente Demo')
  134 |     await page.getByPlaceholder('11 0000-0000').fill('2804000000')
  135 |     await page.getByPlaceholder('Calle y número, piso, depto').fill('Av. Roca 123')
  136 | 
  137 |     // Efectivo debe estar seleccionado por defecto
  138 |     const efectivoBtn = page.getByRole('button', { name: /Efectivo/ }).first()
  139 |     await expect(efectivoBtn).toHaveAttribute('aria-pressed', 'true')
  140 | 
  141 |     // Confirmar pedido
  142 |     await page.getByRole('button', { name: /CONFIRMAR PEDIDO/ }).click()
  143 | 
  144 |     // Pantalla de confirmación
  145 |     await expect(page.getByRole('heading', { name: '¡Pedido confirmado!' })).toBeVisible({ timeout: 5_000 })
  146 | 
  147 |     // Después de 3 s se vuelve al menú; el banner de seguimiento muestra RECEIVED
  148 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 8_000 })
  149 |   })
  150 | })
  151 | 
  152 | // ── Cancelación desde el banner ───────────────────────────────
  153 | 
  154 | test.describe('US-06-F · cancelación de pedido desde el banner', () => {
  155 |   test.beforeEach(async ({ page }) => {
  156 |     await page.addInitScript(({ orderId, storedBranch }) => {
  157 |       localStorage.clear()
  158 |       sessionStorage.clear()
  159 |       localStorage.setItem('laroka_preferred_branch', storedBranch)
  160 |       localStorage.setItem('laroka_active_orders', JSON.stringify([
  161 |         { orderId, branchId: 1 },
  162 |       ]))
  163 |     }, { orderId: CANCEL_ORDER_ID, storedBranch: STORED_BRANCH })
  164 | 
  165 |     await page.route('**/branches', route =>
  166 |       route.fulfill({ json: DEMO_BRANCHES })
  167 |     )
  168 |     await page.route(`**/branches/${BRANCH_ID}/menu`, route =>
  169 |       route.fulfill({ json: [] })
  170 |     )
  171 |   })
  172 | 
  173 |   test('cancelación directa sin motivo: POST enviado con reason null, banner muestra CANCELADO', async ({ page }) => {
  174 |     let capturedBody = null
  175 | 
  176 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  177 |       route.fulfill({ json: makeStatusResponse('RECEIVED') })
  178 |     )
  179 |     await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
  180 |       capturedBody = route.request().postDataJSON()
  181 |       route.fulfill({ status: 204 })
  182 |     })
  183 | 
  184 |     await page.goto('/')
  185 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })
  186 | 
  187 |     await page.getByRole('button', { name: 'Ver detalle' }).click()
  188 |     await page.getByRole('button', { name: 'Cancelar pedido' }).click()
  189 | 
  190 |     // Textarea visible; reason es opcional, confirmar habilitado sin texto
  191 |     await expect(page.locator('textarea')).toBeVisible()
  192 |     const confirmBtn = page.getByRole('button', { name: 'Confirmar cancelación' })
  193 |     await expect(confirmBtn).not.toBeDisabled()
  194 | 
  195 |     await confirmBtn.click()
  196 | 
  197 |     // Banner muestra CANCELADO
  198 |     await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 5_000 })
  199 |     // El body enviado no tiene reason
  200 |     expect(capturedBody?.reason).toBeFalsy()
  201 |   })
  202 | 
  203 |   test('cancelación directa con motivo: reason enviado, banner muestra Entendido (cancelación de cliente)', async ({ page }) => {
  204 |     const REASON = 'cambio de planes'
  205 |     let capturedBody = null
  206 | 
  207 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  208 |       route.fulfill({ json: makeStatusResponse('RECEIVED') })
  209 |     )
  210 |     await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
  211 |       capturedBody = route.request().postDataJSON()
  212 |       route.fulfill({ status: 204 })
  213 |     })
  214 | 
  215 |     await page.goto('/')
  216 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })
```