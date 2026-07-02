import { test as base, expect } from '@playwright/test'

// Origen del backend real (el mismo que consume la app vía VITE_API_URL).
const API_URL = process.env.VITE_API_URL ?? 'http://localhost:8080'

// Fixture hermético compartido por todos los specs e2e.
//
// Problema que resuelve: los specs mockean la API con page.route e inyectan un
// JWT demo. Si un spec olvida mockear una ruta, Playwright deja pasar ese request
// a la red real. Cuando hay un backend corriendo en :8080, éste rechaza el token
// demo con 401 y la app hace logout forzado (window.location = '/login?reason=expired'),
// rompiendo el spec con un timeout opaco esperando un elemento que nunca aparece.
//
// Este catch-all intercepta cualquier request al backend que el spec no haya
// mockeado explícitamente y falla el test nombrando la URL, en vez de filtrarla a
// la red real. Se registra ANTES del cuerpo del test (fixture auto), por lo que los
// page.route específicos del spec —registrados después— tienen prioridad: Playwright
// evalúa las rutas en orden inverso al de registro, así que este fallback solo se
// dispara para rutas del backend genuinamente sin mockear.
export const test = base.extend({
  hermeticBackend: [async ({ page }, use) => {
    await page.route(`${API_URL}/**`, route => {
      const req = route.request()
      throw new Error(
        `[e2e hermético] Ruta al backend no mockeada: ${req.method()} ${req.url()}. ` +
        `Agregá un page.route para esta ruta en el spec (no debe filtrarse a la red real).`,
      )
    })
    await use()
  }, { auto: true }],
})

export { expect }
