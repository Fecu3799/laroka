# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: checkout-flow.spec.js >> US-06-F · cancelación de pedido desde el banner >> cancelación directa sin motivo: POST enviado con reason null, banner muestra CANCELADO
- Location: e2e/checkout-flow.spec.js:219:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-status="RECEIVED"]')
Expected: visible
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for locator('[data-status="RECEIVED"]')

```

```yaml
- status "Cargando La Roka":
  - img "La Roka Pizzería"
```

# Test source

```ts
  131 |     await expect(page.getByText('Muzzarella')).toBeVisible()
  132 | 
  133 |     // Ir a checkout
  134 |     await page.getByRole('button', { name: /IR A PAGAR/ }).click()
  135 | 
  136 |     // Completar datos del cliente
  137 |     await page.getByPlaceholder('¿Cómo te llamás?').fill('Cliente Demo')
  138 |     await page.getByPlaceholder('11 0000-0000').fill('2804000000')
  139 |     await page.getByPlaceholder('Calle y número, piso, depto').fill('Av. Roca 123')
  140 | 
  141 |     // Efectivo debe estar seleccionado por defecto
  142 |     const efectivoBtn = page.getByRole('button', { name: /Efectivo/ }).first()
  143 |     await expect(efectivoBtn).toHaveAttribute('aria-pressed', 'true')
  144 | 
  145 |     // Confirmar pedido
  146 |     await page.getByRole('button', { name: /CONFIRMAR PEDIDO/ }).click()
  147 | 
  148 |     // Pantalla de confirmación
  149 |     await expect(page.getByRole('heading', { name: '¡Pedido confirmado!' })).toBeVisible({ timeout: 5_000 })
  150 | 
  151 |     // Después de 3 s se vuelve al menú; el banner de seguimiento muestra RECEIVED
  152 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 8_000 })
  153 |   })
  154 | 
  155 |   test('bloquea la confirmación cuando acceptingOrders es false (US-08-10)', async ({ page }) => {
  156 |     // El local dejó de aceptar pedidos: override del branch a acceptingOrders:false.
  157 |     await page.route(`**/branches/${BRANCH_ID}`, route =>
  158 |       route.fulfill({ json: { ...DEMO_BRANCHES[0], acceptingOrders: false } })
  159 |     )
  160 |     // Si la confirmación se bloquea correctamente, POST /orders nunca debe dispararse.
  161 |     let orderPosted = false
  162 |     page.on('request', req => {
  163 |       if (req.method() === 'POST' && new URL(req.url()).pathname.endsWith('/orders')) {
  164 |         orderPosted = true
  165 |       }
  166 |     })
  167 | 
  168 |     await page.goto('/')
  169 | 
  170 |     const branchBtn = page.getByRole('button', { name: 'Seleccionar sucursal Puerto Madryn' })
  171 |     await expect(branchBtn).toBeVisible({ timeout: 10_000 })
  172 |     await branchBtn.click()
  173 | 
  174 |     const addBtn = page.getByRole('button', { name: 'Agregar Muzzarella', exact: true })
  175 |     await expect(addBtn).toBeVisible({ timeout: 5_000 })
  176 |     await addBtn.click()
  177 | 
  178 |     await page.getByRole('button', { name: 'Cart' }).click()
  179 |     await page.getByRole('button', { name: /IR A PAGAR/ }).click()
  180 | 
  181 |     await page.getByPlaceholder('¿Cómo te llamás?').fill('Cliente Demo')
  182 |     await page.getByPlaceholder('11 0000-0000').fill('2804000000')
  183 |     await page.getByPlaceholder('Calle y número, piso, depto').fill('Av. Roca 123')
  184 | 
  185 |     await page.getByRole('button', { name: /CONFIRMAR PEDIDO/ }).click()
  186 | 
  187 |     // Aparece el mensaje de bloqueo y NO se navega a la confirmación.
  188 |     await expect(
  189 |       page.getByText('El local no está aceptando pedidos en este momento')
  190 |     ).toBeVisible({ timeout: 5_000 })
  191 |     await expect(
  192 |       page.getByRole('heading', { name: '¡Pedido confirmado!' })
  193 |     ).toHaveCount(0)
  194 |     expect(orderPosted).toBe(false)
  195 |   })
  196 | })
  197 | 
  198 | // ── Cancelación desde el banner ───────────────────────────────
  199 | 
  200 | test.describe('US-06-F · cancelación de pedido desde el banner', () => {
  201 |   test.beforeEach(async ({ page }) => {
  202 |     await page.addInitScript(({ orderId, storedBranch }) => {
  203 |       localStorage.clear()
  204 |       sessionStorage.clear()
  205 |       localStorage.setItem('laroka_preferred_branch', storedBranch)
  206 |       localStorage.setItem('laroka_active_orders', JSON.stringify([
  207 |         { orderId, branchId: 1 },
  208 |       ]))
  209 |     }, { orderId: CANCEL_ORDER_ID, storedBranch: STORED_BRANCH })
  210 | 
  211 |     await page.route('**/branches', route =>
  212 |       route.fulfill({ json: DEMO_BRANCHES })
  213 |     )
  214 |     await page.route(`**/branches/${BRANCH_ID}/menu`, route =>
  215 |       route.fulfill({ json: [] })
  216 |     )
  217 |   })
  218 | 
  219 |   test('cancelación directa sin motivo: POST enviado con reason null, banner muestra CANCELADO', async ({ page }) => {
  220 |     let capturedBody = null
  221 | 
  222 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  223 |       route.fulfill({ json: makeStatusResponse('RECEIVED') })
  224 |     )
  225 |     await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
  226 |       capturedBody = route.request().postDataJSON()
  227 |       route.fulfill({ status: 204 })
  228 |     })
  229 | 
  230 |     await page.goto('/')
> 231 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })
      |                                                            ^ Error: expect(locator).toBeVisible() failed
  232 | 
  233 |     await page.getByRole('button', { name: 'Ver detalle' }).click()
  234 |     await page.getByRole('button', { name: 'Cancelar pedido' }).click()
  235 | 
  236 |     // Textarea visible; reason es opcional, confirmar habilitado sin texto
  237 |     await expect(page.locator('textarea')).toBeVisible()
  238 |     const confirmBtn = page.getByRole('button', { name: 'Confirmar cancelación' })
  239 |     await expect(confirmBtn).not.toBeDisabled()
  240 | 
  241 |     await confirmBtn.click()
  242 | 
  243 |     // Banner muestra CANCELADO
  244 |     await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 5_000 })
  245 |     // El body enviado no tiene reason
  246 |     expect(capturedBody?.reason).toBeFalsy()
  247 |   })
  248 | 
  249 |   test('cancelación directa con motivo: reason enviado, banner muestra Entendido (cancelación de cliente)', async ({ page }) => {
  250 |     const REASON = 'cambio de planes'
  251 |     let capturedBody = null
  252 | 
  253 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  254 |       route.fulfill({ json: makeStatusResponse('RECEIVED') })
  255 |     )
  256 |     await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route => {
  257 |       capturedBody = route.request().postDataJSON()
  258 |       route.fulfill({ status: 204 })
  259 |     })
  260 | 
  261 |     await page.goto('/')
  262 |     await expect(page.locator('[data-status="RECEIVED"]')).toBeVisible({ timeout: 10_000 })
  263 | 
  264 |     await page.getByRole('button', { name: 'Ver detalle' }).click()
  265 |     await page.getByRole('button', { name: 'Cancelar pedido' }).click()
  266 |     await page.locator('textarea').fill(REASON)
  267 |     await page.getByRole('button', { name: 'Confirmar cancelación' }).click()
  268 | 
  269 |     // El reason fue enviado
  270 |     expect(capturedBody?.reason).toBe(REASON)
  271 | 
  272 |     // Banner muestra CANCELADO con botón "Entendido" (cancelación iniciada por cliente)
  273 |     await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 5_000 })
  274 |     await expect(page.getByRole('button', { name: 'Entendido' })).toBeVisible()
  275 | 
  276 |     // Entendido descarta el pedido del banner
  277 |     await page.getByRole('button', { name: 'Entendido' }).click()
  278 |     await expect(page.locator('[data-status="CANCELLED"]')).not.toBeVisible({ timeout: 3_000 })
  279 |   })
  280 | 
  281 |   test('solicitud de cancelación: Confirmar deshabilitado sin texto, habilitado al escribir', async ({ page }) => {
  282 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  283 |       route.fulfill({ json: makeStatusResponse('IN_PREPARATION') })
  284 |     )
  285 |     await page.route(`**/${CANCEL_ORDER_ID}/cancel`, route =>
  286 |       route.fulfill({ status: 204 })
  287 |     )
  288 | 
  289 |     await page.goto('/')
  290 |     await expect(page.locator('[data-status="IN_PREPARATION"]')).toBeVisible({ timeout: 10_000 })
  291 | 
  292 |     await page.getByRole('button', { name: 'Ver detalle' }).click()
  293 |     await page.getByRole('button', { name: 'Solicitar cancelación' }).click()
  294 | 
  295 |     // Confirmar deshabilitado sin texto (motivo obligatorio para CANCELLATION_REQUESTED)
  296 |     const confirmBtn = page.getByRole('button', { name: 'Confirmar solicitud' })
  297 |     await expect(confirmBtn).toBeDisabled()
  298 | 
  299 |     // Al ingresar texto se habilita
  300 |     await page.locator('textarea').fill('ya no quiero el pedido')
  301 |     await expect(confirmBtn).not.toBeDisabled()
  302 | 
  303 |     await confirmBtn.click()
  304 | 
  305 |     // Banner refleja CANCELLATION_REQUESTED
  306 |     await expect(page.locator('[data-status="CANCELLATION_REQUESTED"]')).toBeVisible({ timeout: 5_000 })
  307 |   })
  308 | 
  309 |   test('banner con pedido CANCELLED por operador: modal muestra el motivo y Cerrar lo descarta', async ({ page }) => {
  310 |     const REASON = 'sin stock disponible'
  311 | 
  312 |     await page.route(`**/${CANCEL_ORDER_ID}/status`, route =>
  313 |       route.fulfill({ json: makeStatusResponse('CANCELLED', REASON, true) })
  314 |     )
  315 | 
  316 |     await page.goto('/')
  317 |     await expect(page.locator('[data-status="CANCELLED"]')).toBeVisible({ timeout: 10_000 })
  318 | 
  319 |     // Botón "Ver motivo de cancelación" visible en el banner
  320 |     const viewBtn = page.getByRole('button', { name: 'Ver motivo de cancelación' })
  321 |     await expect(viewBtn).toBeVisible()
  322 |     await viewBtn.click()
  323 | 
  324 |     // Modal con el motivo
  325 |     await expect(page.getByText(REASON)).toBeVisible()
  326 | 
  327 |     // Cerrar descarta el pedido del banner
  328 |     await page.getByRole('button', { name: 'Cerrar' }).click()
  329 |     await expect(page.locator('[data-status="CANCELLED"]')).not.toBeVisible({ timeout: 3_000 })
  330 |   })
  331 | })
```