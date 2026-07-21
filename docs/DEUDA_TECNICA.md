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

## DT-03 — push_subscription.order_id deberá migrar a customer_id

**Estado:** Pendiente  
**US relacionada:** US-EV-01 (registro de clientes)  
**Descripción:** La tabla `push_subscription` asocia la suscripción al `order_id` (pedido). El modelo correcto a largo plazo es asociarla al cliente registrado (`customer_id`). Actualmente no existe entidad de cliente, por lo que el vínculo por pedido es la solución pragmática.  
**Impacto:** Cuando se implemente US-EV-01, las suscripciones push deberán migrarse a `customer_id` y el campo `order_id` deprecarse.  
**Momento:** Resolver al implementar US-EV-01.

## DT-04 — `product_size` sin constraint que ate el tamaño a `category_type.allows_sizes`

**Estado:** Resuelta en el service (US-SIZE-04) — sin constraint a nivel base
**US relacionada:** US-SIZE-01 (introduce la tabla), US-SIZE-04 (agrega la validación)
**Descripción:** La regla "solo productos cuya categoría tenga `allows_sizes = true` pueden tener filas en `product_size`" no es expresable como constraint declarativo en PostgreSQL: requiere atravesar `product → category → category_type`, lo que exigiría un trigger o una FK compuesta redundante.
**Resolución:** `ProductSizeService.create` valida la categoría y rechaza con 422. Lo mismo aplica a la restricción de que sólo `CHICA` puede existir como fila (GRANDE es implícito): se valida en el service, no en el enum, porque `ProductSizeName` conserva ambos valores.
**Riesgo remanente:** Una carga manual por TablePlus sigue pudiendo violar ambas reglas sin error. En el caso de `GRANDE` el efecto es peor que una inconsistencia: el client filtra esas filas, así que la fila quedaría invisible y sin uso. Aceptado mientras el backoffice sea la única vía de escritura real.
**Momento:** Evaluar un trigger sólo si aparece una segunda vía de escritura automatizada.

---

## MP Webhook Signature Validation — Sandbox Only

**Estado:** Workaround activo  
**Variable:** `SKIP_WEBHOOK_SIGNATURE_VALIDATION=true` (solo en staging/sandbox)  
**Descripción:** En modo sandbox, MercadoPago firma los webhooks con un secret que no coincide con el que expone en el panel de desarrolladores. El HMAC calculado nunca coincide aunque el secret, el mensaje y el algoritmo sean correctos. Verificado con SHA256 del secret y logs de ambos hashes.  
**Impacto:** Solo afecta pruebas con credenciales TEST-. En producción con credenciales reales el webhook llega y se procesa correctamente.  
**Workaround:** Flag `SKIP_WEBHOOK_SIGNATURE_VALIDATION=true` en Render para ambiente de pruebas. En producción esta variable debe estar en `false` o ausente.