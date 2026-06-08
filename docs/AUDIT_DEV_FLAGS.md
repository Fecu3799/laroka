# Auditoría de Flags y Variables de Desarrollo/Debug

Fecha: 2026-06-08  
Rama: `features/quick-fixes`

---

## Índice

1. [Backend — Propiedades de Spring](#1-backend--propiedades-de-spring)
2. [Backend — Feature flags en código Java](#2-backend--feature-flags-en-código-java)
3. [Frontend — Variables de entorno Vite](#3-frontend--variables-de-entorno-vite)
4. [Frontend — Flags hardcodeadas en código JS](#4-frontend--flags-hardcodeadas-en-código-js)
5. [Infraestructura](#5-infraestructura)
6. [Tabla resumen](#6-tabla-resumen)
7. [Propuesta de centralización](#7-propuesta-de-centralización)

---

## 1. Backend — Propiedades de Spring

### 1.1 `application.yml` (perfil por defecto)

| # | Propiedad | Archivo:Línea | Dev | Prod | Qué controla |
|---|-----------|--------------|-----|------|-------------|
| B-01 | `order.bypass-branch-hours` | `application.yml:46` | `true` | debe ser `false` | **Omite validación de horarios de sucursal al crear pedidos** |
| B-02 | `swagger.enabled` | `application.yml:56` | `false` | `false` | Activa/desactiva endpoints de Swagger/OpenAPI |
| B-03 | `r2.bucket-name` | `application.yml:51` | `"laroka-dev"` | bucket prod | Nombre del bucket Cloudflare R2 |
| B-04 | `cors.allowed-origins` | `application.yml:38` | `localhost:5173, localhost:5174, 192.168.1.114:5173` | dominios prod | Orígenes CORS permitidos |
| B-05 | `jwt.expiration` | `application.yml:35` | `28800000` (8 h) | configurable vía env | Expiración del JWT en milisegundos |
| B-06 | `order.expiration-minutes` | `application.yml:45` | `30` | configurable | Tiempo de expiración de pedidos sin pago |
| B-07 | `management.endpoints.web.exposure.include` | `application.yml:28` | `health` | `health` | Endpoints de Actuator expuestos (solo health) |
| B-08 | `spring.jpa.show-sql` | `application.yml:15` | `false` | `false` | Logueo de queries SQL |

### 1.2 `application-local.yml` (perfil `local`)

| # | Propiedad | Archivo:Línea | Valor | Qué controla |
|---|-----------|--------------|-------|-------------|
| B-09 | `spring.datasource.url` | `application-local.yml:3` | `jdbc:postgresql://localhost:5432/laroka` | Conexión DB local |
| B-10 | `spring.datasource.username` | `application-local.yml:4` | `"postgres"` | Usuario DB local |
| B-11 | `spring.datasource.password` | `application-local.yml:5` | `"postgres"` | Password DB local (hardcodeado) |
| B-12 | `jwt.secret` | `application-local.yml:21` | clave de prueba hardcodeada | Secreto para firmar JWTs en local |
| B-13 | `mercadopago.key` | `application-local.yml:25` | clave sandbox hardcodeada | Access token de MercadoPago sandbox |
| B-14 | `mercadopago.notifications-url` | `application-local.yml:27` | URL de ngrok pública | Webhook de MercadoPago para local — URL pública temporal |
| B-15 | `logging.level.com.laroka.order` | `application-local.yml:18` | `DEBUG` | Logging elevado para el módulo de pedidos |

### 1.3 `application-test.yml` (perfil `test`)

| # | Propiedad | Archivo:Línea | Valor | Qué controla |
|---|-----------|--------------|-------|-------------|
| B-16 | `spring.datasource.url` | `application-test.yml:3` | `jdbc:h2:mem:testdb;MODE=PostgreSQL` | Base de datos H2 en memoria para tests |
| B-17 | `spring.jpa.hibernate.ddl-auto` | `application-test.yml:9` | `none` | No auto-crear schema en tests |
| B-18 | `jwt.secret` | `application-test.yml:21` | `"test-secret-minimum-32-chars-for-hmac256-ok"` | Secreto JWT hardcodeado para tests |
| B-19 | `jwt.expiration` | `application-test.yml:22` | `3600000` (1 h) | Expiración JWT en tests |

---

## 2. Backend — Feature flags en código Java

### 2.1 `PaymentService.java`

| # | Variable | Propiedad | Archivo:Línea | Default | Qué controla |
|---|----------|-----------|--------------|---------|-------------|
| B-20 | `skipWebhookSignatureValidation` | `debug.skip-webhook-signature-validation` | `PaymentService.java:55` | `false` | **FLAG DE DEBUG: omite validación HMAC de webhooks de MercadoPago** |
| B-21 | `webhookSecret` | `mercadopago.webhook-secret` | `PaymentService.java:52` | `""` | Secreto para verificar firma de webhooks |

Cuando `B-20` es `true`, el servicio loguea: `"SIGNATURE VALIDATION SKIPPED (debug flag active)"` (`PaymentService.java:282`).

### 2.2 `OrderService.java`

| # | Variable | Propiedad | Archivo:Línea | Default | Qué controla |
|---|----------|-----------|--------------|---------|-------------|
| B-22 | `bypassBranchHours` | `order.bypass-branch-hours` | `OrderService.java:59` | `true` | **Omite validación de horarios de atención al crear pedidos** |

Cuando es `true` loguea: `"Branch hours check bypassed — order.bypass-branch-hours=true"` (`OrderService.java:383`).

**Riesgo: el default en `application.yml` es `true`, lo que significa que en producción también se omite la validación si la variable de entorno no está seteada.**

### 2.3 `OpenApiConfig.java`

| # | Anotación | Propiedad | Archivo:Línea | Qué controla |
|---|-----------|-----------|--------------|-------------|
| B-23 | `@ConditionalOnProperty` | `swagger.enabled` | `OpenApiConfig.java:14` | Habilita el bean de Swagger/OpenAPI condicionalmente. `matchIfMissing=true` → activo por default si la propiedad no existe |

### 2.4 Tests — anotaciones de perfil

| # | Anotación | Archivo:Línea | Valor | Qué controla |
|---|-----------|--------------|-------|-------------|
| B-24 | `@ActiveProfiles` | `OrderFlowsIntegrationTest.java:46` | `"test"` | Activa perfil `test` para tests de integración |
| B-25 | Constante `JWT_SECRET` | `OrderFlowsIntegrationTest.java:50` | valor hardcodeado | Secreto JWT en tests de integración |
| B-26 | `@TestPropertySource` | `SecurityIntegrationTest.java:39` | props hardcodeadas | Sobreescribe propiedades para tests de seguridad |
| B-27 | Constante `TEST_SECRET` | `SecurityIntegrationTest.java:46` | valor hardcodeado | Secreto JWT hardcodeado en test de seguridad |

---

## 3. Frontend — Variables de entorno Vite

### 3.1 `client/.env.local`

| # | Variable | Archivo | Valor | Qué controla |
|---|----------|---------|-------|-------------|
| F-01 | `VITE_API_URL` | `client/.env.local` | `http://localhost:8080` | URL base del backend |

### 3.2 `client/.env.example`

| # | Variable | Valor de ejemplo | Qué controla |
|---|----------|-----------------|-------------|
| F-02 | `VITE_API_URL` | `http://localhost:8080` | URL base del backend |
| F-03 | `VITE_APP_URL` | `http://localhost:5173` | URL propia del cliente (usada en redirecciones de MP) |

### 3.3 `backoffice/.env.local`

| # | Variable | Archivo | Valor | Qué controla |
|---|----------|---------|-------|-------------|
| F-04 | `VITE_API_URL` | `backoffice/.env.local` | `http://localhost:8080` | URL base del backend |

### 3.4 Fallbacks hardcodeados en servicios JS

Los siguientes archivos definen `API_BASE` con fallback a localhost si `VITE_API_URL` no está definida:

| # | Archivo | Línea | Fallback |
|---|---------|-------|---------|
| F-05 | `client/src/services/paymentsService.js` | 3 | `http://localhost:8080` |
| F-06 | `client/src/services/ordersService.js` | 3 | `http://localhost:8080` |
| F-07 | `client/src/services/paymentsService.js` | 4 | `http://localhost:5173` (APP_URL) |
| F-08 | `client/src/components/CartScreen.jsx` | 3 | `http://localhost:8080` |
| F-09 | `client/src/components/CartScreen.jsx` | 4 | `http://localhost:5173` |
| F-10 | `client/src/App.jsx` | — | `http://localhost:8080` |
| F-11 | `client/src/components/BranchSelection.jsx` | — | `http://localhost:8080` |
| F-12 | `client/src/components/OrderTrackingBanner.jsx` | — | `http://localhost:8080` |
| F-13 | `client/src/components/SplashScreen.jsx` | — | `http://localhost:8080` |

Backoffice usa `??` en lugar de `||`, con fallback a string vacío:

| # | Archivo | Línea | Fallback |
|---|---------|-------|---------|
| F-14 | `backoffice/src/services/authService.js` | 1 | `''` (string vacío) |
| F-15 | `backoffice/src/services/ordersService.js` | 3 | `''` |
| F-16 | `backoffice/src/services/catalogService.js` | 3 | `''` |
| F-17 | `backoffice/src/services/branchService.js` | 3 | `''` |
| F-18 | `backoffice/src/hooks/useOrders.js` | 2 | `''` |

---

## 4. Frontend — Flags hardcodeadas en código JS

### 4.1 `CheckoutScreen.jsx` — Debug fill form

| # | Identificador | Archivo:Línea | Comportamiento | Activo en |
|---|--------------|--------------|---------------|----------|
| F-19 | `_DEBUG_COUNT_KEY` | `CheckoutScreen.jsx:5` | Clave de localStorage para contar cuántas veces se usó el debug fill | Solo registrado |
| F-20 | `handleDebugFill()` | `CheckoutScreen.jsx:98` | Autocompleta el formulario de checkout con datos ficticios | `import.meta.env.DEV === true` |
| F-21 | Botón "🛠 Fill Debug Data" | `CheckoutScreen.jsx:176` | Renderiza botón de debug en el formulario | `import.meta.env.DEV === true` |

### 4.2 Console.log con prefijo `[MP-DEBUG]`

Logs de debug de MercadoPago presentes en producción (no condicionados a DEV):

| # | Archivo:Línea | Mensaje |
|---|--------------|---------|
| F-22 | `CheckoutScreen.jsx:113` | `[MP-DEBUG] visibilitychange fired` |
| F-23 | `CartScreen.jsx:164` | `[MP-DEBUG] handleMpReturn called — orderId: ...` |
| F-24 | `CartScreen.jsx:171` | `[MP-DEBUG] order status response — status: ...` |
| F-25 | `PaymentModals.jsx:80` | `[MP-DEBUG] PendingPaymentModal mounted — orderId: ...` |

**Nota: F-22 a F-25 no están condicionados a `import.meta.env.DEV`, por lo que se ejecutan en producción.**

---

## 5. Infraestructura

### 5.1 `.env` — Credenciales de desarrollo en el repo

| # | Variable | Archivo | Valor | Riesgo |
|---|----------|---------|-------|--------|
| I-01 | `DB_URL` | `.env` | `jdbc:postgresql://localhost:5432/laroka` | — |
| I-02 | `DB_USER` | `.env` | `postgres` | — |
| I-03 | `DB_PASS` | `.env` | `postgres` | Credencial hardcodeada |
| I-04 | `R2_ACCESS_KEY` | `.env` | clave real de R2 | **CRÍTICO: secreto real en repo** |
| I-05 | `R2_SECRET_KEY` | `.env` | clave real de R2 | **CRÍTICO: secreto real en repo** |
| I-06 | `JWT_SECRET` | `.env` | clave real | **CRÍTICO: secreto real en repo** |
| I-07 | `MERCADOPAGO_ACCESS_TOKEN` | `.env` | token sandbox | Credential de sandbox |
| I-08 | `MERCADOPAGO_WEBHOOK_SECRET` | `.env` | secreto real | **CRÍTICO: secreto real en repo** |

### 5.2 `docker-compose.yml`

| # | Variable | Archivo:Línea | Valor | Qué controla |
|---|----------|--------------|-------|-------------|
| I-09 | `POSTGRES_USER` | `docker-compose.yml:8` | `postgres` | Usuario DB en Docker local |
| I-10 | `POSTGRES_PASSWORD` | `docker-compose.yml:9` | `postgres` | Password DB en Docker local |
| I-11 | `POSTGRES_DB` | `docker-compose.yml:10` | `laroka` | Nombre de la DB en Docker local |

### 5.3 `backend/Dockerfile`

| # | Flag JVM | Archivo:Línea | Qué controla |
|---|----------|--------------|-------------|
| I-12 | `-javaagent:/app/newrelic/newrelic.jar` | `Dockerfile:27` | Agente APM de New Relic inyectado en imagen de producción |
| I-13 | `-Dnewrelic.config.app_name=laroka-backend` | `Dockerfile:28` | Nombre de la app en New Relic |
| I-14 | `-Dnewrelic.config.log_file_name=STDOUT` | `Dockerfile:29` | Logs de New Relic a stdout |

### 5.4 `client/vite.config.js`

| # | Configuración | Línea | Qué controla |
|---|--------------|-------|-------------|
| I-15 | `server.host: true` | `vite.config.js:54` | Dev server accesible desde la red local (no solo localhost) |

---

## 6. Tabla resumen

| ID | Tipo | Nombre | Ubicación | Default dev | Default prod | Riesgo |
|----|------|--------|-----------|-------------|--------------|--------|
| B-01 | Property | `order.bypass-branch-hours` | `application.yml:46` | `true` | `true` ⚠️ | **ALTO** |
| B-20 | Property | `debug.skip-webhook-signature-validation` | `PaymentService.java:55` | `false` | `false` | ALTO si se activa |
| B-23 | Conditional | `swagger.enabled` | `OpenApiConfig.java:14` | activo por default | desactivado | MEDIO |
| B-12 | Hardcoded | `jwt.secret` local | `application-local.yml:21` | clave de prueba | — | MEDIO |
| B-14 | Hardcoded | URL ngrok | `application-local.yml:27` | URL pública temp | — | MEDIO |
| B-15 | Property | `logging.level.com.laroka.order` | `application-local.yml:18` | `DEBUG` | `INFO` | BAJO |
| F-20 | Code flag | `handleDebugFill` | `CheckoutScreen.jsx:98` | activo | inactivo | BAJO |
| F-22~25 | Console.log | `[MP-DEBUG]` | múltiples archivos | activos | **activos** ⚠️ | BAJO |
| F-05~18 | Fallback | `localhost:8080` | múltiples servicios | localhost | sin env → falla silenciosa | BAJO |
| I-04~08 | Secrets | R2/JWT/MP en `.env` | `.env` | real | — | **CRÍTICO** |

---

## 7. Propuesta de centralización

> Esta sección es una propuesta. No se implementa nada aquí.

### Principio

Unificar todas las flags de comportamiento de desarrollo bajo una variable maestra `APP_ENV` con subvariables explícitas. Ningún flag de comportamiento debería tener un default peligroso en el YAML base — todos deben llegar desde variables de entorno, con defaults seguros.

### Variable maestra

```
APP_ENV=development | production | test
```

Los entornos de despliegue (Render, Vercel, CI) setean esta variable. Spring puede leer `APP_ENV` para activar el perfil correcto, reemplazando `SPRING_PROFILES_ACTIVE`.

### Subvariables propuestas

| Variable | Tipo | Default seguro | Reemplaza | Controla |
|----------|------|---------------|-----------|---------|
| `APP_DEV_BYPASS_BRANCH_HOURS` | boolean | `false` | `order.bypass-branch-hours` (B-01/B-22) | Permitir pedidos fuera de horario |
| `APP_DEV_SKIP_WEBHOOK_VALIDATION` | boolean | `false` | `debug.skip-webhook-signature-validation` (B-20) | Omitir validación HMAC de webhooks |
| `APP_DEV_SWAGGER_ENABLED` | boolean | `false` | `swagger.enabled` (B-02/B-23) | Exponer endpoints de Swagger |
| `APP_DEV_LOG_LEVEL` | string | `INFO` | `logging.level.com.laroka.order` (B-15) | Nivel de logging del módulo de pedidos |
| `APP_DEV_CORS_ORIGINS` | string | — | `cors.allowed-origins` (B-04) | Orígenes CORS (siempre vía env) |
| `VITE_DEV_DEBUG_FILL` | boolean | `false` | `import.meta.env.DEV` check (F-20) | Botón de debug fill en checkout |
| `VITE_DEV_MP_DEBUG_LOGS` | boolean | `false` | console.logs con `[MP-DEBUG]` (F-22~25) | Logs de debug de MercadoPago |

### Esquema propuesto para `application.yml` (solo defaults seguros)

```yaml
order:
  bypass-branch-hours: ${APP_DEV_BYPASS_BRANCH_HOURS:false}   # default seguro
  expiration-minutes: ${ORDER_EXPIRATION_MINUTES:30}

debug:
  skip-webhook-signature-validation: ${APP_DEV_SKIP_WEBHOOK_VALIDATION:false}

swagger:
  enabled: ${APP_DEV_SWAGGER_ENABLED:false}

logging:
  level:
    com.laroka: ${APP_LOG_LEVEL:INFO}
    com.laroka.order: ${APP_DEV_LOG_LEVEL:INFO}
```

### Esquema propuesto para `.env.example` (backend)

```dotenv
# Entorno
APP_ENV=development

# Feature flags de desarrollo (false en producción)
APP_DEV_BYPASS_BRANCH_HOURS=true
APP_DEV_SKIP_WEBHOOK_VALIDATION=false
APP_DEV_SWAGGER_ENABLED=true
APP_DEV_LOG_LEVEL=DEBUG

# Infraestructura (nunca hardcodear)
DB_URL=jdbc:postgresql://localhost:5432/laroka
DB_USER=postgres
DB_PASS=postgres
JWT_SECRET=<generar con openssl rand -base64 48>
JWT_EXPIRATION=28800000
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174

# Cloudflare R2
R2_BUCKET_NAME=laroka-dev
R2_ENDPOINT=
R2_ACCESS_KEY=
R2_SECRET_KEY=

# MercadoPago
MERCADOPAGO_ACCESS_TOKEN=
MERCADOPAGO_WEBHOOK_SECRET=
```

### Esquema propuesto para `.env.example` (frontend)

```dotenv
VITE_API_URL=http://localhost:8080
VITE_APP_URL=http://localhost:5173
VITE_DEV_DEBUG_FILL=true
VITE_DEV_MP_DEBUG_LOGS=false
```

### Acciones pendientes (no implementadas)

1. **CRÍTICO**: Rotar las credenciales expuestas en `.env` (R2, JWT, webhook) — pueden estar en git history.
2. **ALTO**: Cambiar default de `order.bypass-branch-hours` a `false` en `application.yml`.
3. **ALTO**: Agregar `APP_DEV_BYPASS_BRANCH_HOURS` y `APP_DEV_SKIP_WEBHOOK_VALIDATION` al `.env.example`.
4. **MEDIO**: Condicionar logs `[MP-DEBUG]` a `VITE_DEV_MP_DEBUG_LOGS` en lugar de dejarlos siempre activos.
5. **MEDIO**: Cambiar `swagger.enabled` en `OpenApiConfig.java` a `matchIfMissing=false` para que sea opt-in.
6. **BAJO**: Centralizar todas las declaraciones de `API_BASE` en un único módulo de configuración en cada frontend.
7. **BAJO**: Verificar que `.env` esté en `.gitignore` y no en el historial de git.
