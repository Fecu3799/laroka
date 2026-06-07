# Deuda Técnica Activa

| ID | Descripción | Sprint | US relacionada |
|---|---|---|---|
| ~~DT-01~~ | ~~Spring Security deshabilitado temporalmente. Todos los endpoints son públicos. Requiere configuración de rutas públicas y protegidas con filtro JWT.~~ | ~~Sprint 2~~ | ~~US-02-03~~ | **RESUELTA** |

## DT-02 — Vulnerabilidades en dependencias de build (client)

| Campo       | Detalle                                                                 |
| ----------- | ----------------------------------------------------------------------- |
| Severidad   | Alta (build only, no afecta bundle de producción)                       |
| Paquete     | `serialize-javascript <= 7.0.4`                                         |
| Cadena      | `vite-plugin-pwa → workbox-build → @rollup/plugin-terser → serialize-javascript` |
| Riesgo real | Bajo — solo afecta el proceso de build, no el código que llega al usuario |
| Resolución  | Esperar actualización de `vite-plugin-pwa` que actualice `workbox-build`. No usar `npm audit fix --force` — instalaría una versión anterior del plugin y rompería la configuración PWA. |
| Momento     | Resolver antes del lanzamiento a producción                             |

## MP Webhook Signature Validation — Sandbox Only

**Estado:** Workaround activo  
**Variable:** `SKIP_WEBHOOK_SIGNATURE_VALIDATION=true` (solo en staging/sandbox)  
**Descripción:** En modo sandbox, MercadoPago firma los webhooks con un secret que no coincide con el que expone en el panel de desarrolladores. El HMAC calculado nunca coincide aunque el secret, el mensaje y el algoritmo sean correctos. Verificado con SHA256 del secret y logs de ambos hashes.  
**Impacto:** Solo afecta pruebas con credenciales TEST-. En producción con credenciales reales el webhook llega y se procesa correctamente.  
**Workaround:** Flag `SKIP_WEBHOOK_SIGNATURE_VALIDATION=true` en Render para ambiente de pruebas. En producción esta variable debe estar en `false` o ausente.