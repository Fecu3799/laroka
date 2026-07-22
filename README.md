# PediSur — Sistema de Gestión de Pedidos

Sistema de gestión digital de pedidos para pizzería multi-sucursal, concebido como base evolutiva hacia una plataforma multi-comercio.

---

## Descripción

PediSur digitaliza y centraliza el flujo de pedidos entre clientes y sucursales, cubriendo desde la selección del menú hasta el seguimiento operativo en tiempo real. El sistema está compuesto por una aplicación cliente (PWA mobile-first), un backoffice para el personal del local y un backend centralizado con lógica de negocio.

---

## Estado actual

En producción: flujo completo de pedido cliente → pago (MercadoPago / efectivo) → gestión operativa en backoffice con actualizaciones en tiempo real (SSE) y Web Push, soporte multi-sucursal con roles ADMIN/MANAGER/STAFF, y turnos con cierre de caja por sucursal. Deploy automatizado vía CI/CD (backend en Render, frontends en Vercel).

> Deuda técnica y limitaciones conocidas documentadas en `docs/DEUDA_TECNICA.md`.

---

## Breaking Changes

### Sprint 13 — `GET /branches` ahora requiere `tenantId` (US-13-01)

El endpoint público `GET /branches` pasó a **requerir** el query param
`tenantId` (entero). Antes devolvía todas las sucursales sin filtro; ahora
devuelve únicamente las del tenant indicado.

- **Antes:** `GET /branches`
- **Ahora:** `GET /branches?tenantId=1`
- Omitir `tenantId` retorna **400 Bad Request**.

**Impacto en la PWA del cliente:** las pantallas que listan sucursales
(`BranchSelection`, `SplashScreen`) deben incluir el `tenantId` en la llamada.
La integración del lado del frontend se aborda en US-13-F-01 mediante la
variable de entorno `VITE_TENANT_ID`.

---

## Stack Tecnológico

| Componente          | Tecnología                                    |
| ------------------- | --------------------------------------------- |
| Backend             | Java 21 + Spring Boot 3.4                     |
| Base de datos       | PostgreSQL 16                                 |
| Migraciones         | Flyway                                        |
| Frontend cliente    | React + Vite (PWA)                            |
| Frontend backoffice | React + Vite                                  |
| Storage multimedia  | Cloudflare R2                                 |
| Proveedor de pagos  | MercadoPago                                   |
| Cache               | Caffeine (Spring Cache)                       |
| Notificaciones      | SSE (backoffice) + Web Push / VAPID (cliente) |
| Hosting backend     | Render                                        |
| Hosting frontends   | Vercel                                        |
| CI/CD               | GitHub Actions                                |

---

## Arquitectura

Monolito modular. El backend centraliza toda la lógica de negocio organizado en módulos independientes con separación explícita de capas.

```
pedisur/
├── backend/                  # Spring Boot — API REST
│   └── src/main/java/com/pedisur/backend/
│       ├── auth/             # Autenticación JWT y refresh tokens
│       ├── branch/           # Sucursales
│       ├── catalog/          # Productos y categorías
│       ├── notification/     # SSE al backoffice + Web Push al cliente
│       ├── order/            # Ciclo de vida del pedido
│       ├── payment/          # Integración MercadoPago
│       ├── shift/            # Turnos y cierre de caja por sucursal
│       ├── staffuser/        # Usuarios internos
│       ├── tenant/           # Entidad raíz multi-tenant (multi-pizzería)
│       └── shared/           # Excepciones y utilidades comunes
├── client/                   # React PWA — Interfaz del cliente
├── backoffice/               # React — Interfaz del personal
└── docs/                     # Documentación del proyecto
    ├── BACKLOG.md
    ├── DEUDA_TECNICA.md
    ├── AUDIT_DEV_FLAGS.md
    ├── COLORS.md
    └── informe_tecnico.md
```

### Capas por módulo

```
Controller → Service → Repository
     ↕           ↕
    DTO        Entity
     ↕
  Mapper
```

- **Controller**: expone endpoints REST, sin lógica de negocio
- **Service**: toda la lógica de negocio, recibe y retorna entidades
- **Repository**: acceso a datos vía Spring Data JPA
- **DTO**: objetos de transferencia, nunca se exponen entidades directamente
- **Mapper**: conversión entidad ↔ DTO en el Controller

### Roles y autorización

Tres roles activos: **ADMIN**, **MANAGER**, **STAFF**.

| Rol     | Alcance                                                                  | branchId                                          |
| ------- | ------------------------------------------------------------------------ | ------------------------------------------------- |
| ADMIN   | Dueño del tenant, opera sobre cualquier sucursal                         | JWT sin branchId; envía `X-Branch-Id` por request |
| MANAGER | Encargado de sucursal: abre/cierra turnos, ve resúmenes y cierre de caja | branchId en el JWT                                |
| STAFF   | Empleado operativo: solo gestión de pedidos                              | branchId en el JWT                                |

La resolución del branchId está centralizada en `SecurityUtils.resolveBranchId`:
STAFF/MANAGER lo leen del token; ADMIN lo toma del header `X-Branch-Id`
(400 si falta o es inválido, 403 si no pertenece al tenant del token).

---

## Requisitos

- Java 21
- Docker Desktop
- Node.js 18+
- Maven (incluido via Maven Wrapper)

---

## Configuración local

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/pedisur.git
cd pedisur
```

### 2. Variables de entorno

Crear `client/.env.local`, `backoffice/.env.local` basándose en los archivo de ejemplo:

```bash
cp client/.env.example client/.env.local
```

```bash
cp backoffice/.env.example backoffice/.env.local
```

Para el backend, las variables se configuran en
`backend/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pedisur
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          "time_zone": UTC
    show-sql: true

logging:
  structured:
    format:
      console:

jwt:
  secret: <JWT_SECRET>
  expiration: 28800000
```

Las variables sensibles (R2, MercadoPago, JWT) se
configuran como variables de entorno del sistema o
en el archivo `.env` en la raíz del repo (ignorado por git).

Variables requeridas:

```env
# Base de datos
DB_URL=jdbc:postgresql://localhost:5432/pedisur
DB_USER=postgres
DB_PASS=postgres

# Cloudflare R2
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_BUCKET_NAME=pedisur-dev
R2_ENDPOINT=

# MercadoPago
MERCADOPAGO_KEY=
MERCADOPAGO_WEBHOOK_SECRET=

# JWT
JWT_SECRET=
# Opcional — solo durante una rotación de JWT_SECRET (grace period).
# Vacío en operación normal. Ver "Seguridad — Rotación de Secretos".
JWT_SECRET_PREVIOUS=
JWT_EXPIRATION=28800000

# Web Push (VAPID)
VAPID_PUBLIC_KEY=
VAPID_PRIVATE_KEY=
```

```env
# client/.env.local
VITE_API_URL=http://localhost:8080
```

### 3. Levantar la base de datos

```bash
docker compose up -d
```

### 4. Levantar el backend

```bash
make run-back
```

El backend queda disponible en `http://localhost:8080`.

Documentación de la API disponible en `http://localhost:8080/swagger-ui.html`.

### 5. Levantar el frontend cliente

```bash
cd client
npm install
npm run dev
```

Disponible en `http://localhost:5173`.

### 6. Levantar el backoffice

```bash
cd backoffice
npm install
npm run dev
```

Disponible en `http://localhost:5174`.

---

## Desarrollo local — webhooks

Para recibir webhooks de MercadoPago en local se necesita exponer el backend con ngrok.

### 1. Instalar ngrok

```bash
brew install ngrok
```

O descargar desde https://ngrok.com/download e instalar manualmente.

### 2. Autenticar ngrok (una sola vez)

```bash
ngrok config add-authtoken <tu-authtoken>
```

### 3. Exponer el backend

Con el backend corriendo en el puerto 8080:

```bash
ngrok http 8080
```

ngrok muestra una URL pública del tipo `https://xxxx-xxxx.ngrok-free.app`.

### 4. Configurar la URL en MercadoPago sandbox

1. Ingresar al [panel de desarrolladores de MercadoPago](https://www.mercadopago.com.ar/developers/panel/app).
2. Seleccionar la aplicación de sandbox.
3. En **Webhooks → Configurar notificaciones**, ingresar la URL:
   ```
   https://xxxx-xxxx.ngrok-free.app/payments/webhook
   ```
4. Seleccionar el evento **Pagos**.
5. Copiar el **secreto de firma** generado por MP y agregarlo al entorno:
   ```env
   MERCADOPAGO_WEBHOOK_SECRET=<secreto-copiado>
   ```

### 5. Testear en local sin credenciales MP

Si `MERCADOPAGO_KEY` está vacío, el adapter opera en modo dev:

- `POST /payments/initiate` retorna una URL de sandbox simulada.
- `POST /payments/webhook` con `{"type":"payment","data":{"id":"<orderId>"}}` activa el pedido directamente (el `id` se trata como `orderId`).
- La validación de firma se omite cuando `MERCADOPAGO_WEBHOOK_SECRET` está vacío.

---

## Comandos útiles

```bash
# Backend
make run-back   # Levantar backend con perfil local
make test       # Correr tests
make build      # Build sin tests

make run-client       # Levantar frontend cliente con perfil local
make run-backoffice   # Levantar frontend backoffice con perfil local

# Base de datos
docker compose up -d      # Levantar PostgreSQL
docker compose down       # Detener
docker compose down -v    # Detener y borrar volúmenes (reset completo)
```

---

## CI/CD

El proyecto tiene workflows de CI (en PR) y CD (en push a main) por componente.

**CI** — corre en cada PR a `main` con cambios en el path correspondiente:

| Workflow          | Trigger                       | Steps                                                       |
| ----------------- | ----------------------------- | ----------------------------------------------------------- |
| ci-backend.yml    | PR con cambios en backend/    | lint (Checkstyle) → build → tests (JUnit + IntegrationTest) |
| ci-client.yml     | PR con cambios en client/     | lint (ESLint) → build → unit (Vitest) → E2E (Playwright)    |
| ci-backoffice.yml | PR con cambios en backoffice/ | lint (ESLint) → build → unit (Vitest) → E2E (Playwright)    |

Todo merge a main requiere PR con CI en verde.

**CD** — corre en push a `main` con cambios en el path correspondiente:

| Workflow          | Trigger                    | Acción                                                                             |
| ----------------- | -------------------------- | ---------------------------------------------------------------------------------- |
| cd-backend.yml    | push a main en backend/    | build & push de imagen Docker (taggeada por SHA + digest SHA256) → deploy a Render |
| cd-client.yml     | push a main en client/     | deploy a Vercel (PWA)                                                              |
| cd-backoffice.yml | push a main en backoffice/ | deploy a Vercel                                                                    |

---

## Seguridad

### Content Security Policy (CSP)

Ambos frontends se sirven en Vercel con cabeceras de seguridad definidas en
`client/vercel.json` y `backoffice/vercel.json`, incluyendo una CSP restrictiva
(`default-src 'self'`, `frame-ancestors 'none'`, etc.). Las fuentes de
`@fontsource` se embeben como data URIs en el CSS compilado, por lo que la CSP
incluye `font-src 'self' data:`. El cliente además permite los orígenes de
MercadoPago necesarios para el checkout (`script-src`, `frame-src`, `connect-src`).

### Rotación de Secretos

Procedimiento para rotar cada secreto crítico. Salvo que se indique lo
contrario, "actualizar en Render" significa editar la variable de entorno en
el dashboard de Render (Environment) y disparar un redeploy del servicio
backend.

### JWT_SECRET (con grace period, sin cortar sesiones activas)

El backend soporta un segundo secret opcional `JWT_SECRET_PREVIOUS`. Durante la
validación se intenta primero con `JWT_SECRET`; si falla y `JWT_SECRET_PREVIOUS`
está configurado, se reintenta con él. Esto permite rotar sin invalidar de
golpe los tokens ya emitidos.

Pasos:

1. Generar un nuevo secret seguro (≥32 bytes para HMAC-SHA256), por ejemplo:
   `openssl rand -base64 48`.
2. En Render, setear `JWT_SECRET_PREVIOUS` = valor **actual** de `JWT_SECRET`.
3. En Render, setear `JWT_SECRET` = nuevo secret generado en el paso 1.
4. Redeploy. A partir de acá los tokens nuevos se firman con el secret nuevo y
   los viejos (firmados con el anterior) siguen validando vía
   `JWT_SECRET_PREVIOUS`.
5. Esperar a que expire el TTL de los tokens viejos (`JWT_EXPIRATION`, default
   8 horas / `28800000` ms). Pasado ese plazo ya no quedan tokens firmados con
   el secret anterior en circulación.
6. Limpiar `JWT_SECRET_PREVIOUS` (dejarlo vacío) y redeploy. Fin del grace
   period.

> **Advertencia (riesgo conocido y acotado).** La blacklist de JWT es
> in-memory y no persiste entre reinicios del servidor. Durante una rotación
> que implique redeploy, los tokens previamente revocados por logout —firmados
> con el secret anterior— pueden volver a ser aceptados por el grace period
> durante el TTL restante del token. El riesgo está acotado por el TTL
> configurado (`JWT_EXPIRATION`, default 8 horas): vencido el TTL, esos tokens
> dejan de ser válidos de todos modos. No es un gate de seguridad nuevo, es una
> consecuencia conocida de combinar grace period + blacklist no persistente.

### MERCADOPAGO_WEBHOOK_SECRET

1. En el panel de MercadoPago (Tus integraciones → la aplicación → Webhooks /
   Notificaciones), generar/regenerar la firma secreta del webhook.
2. Copiar el nuevo valor de la clave secreta.
3. En Render, actualizar `MERCADOPAGO_WEBHOOK_SECRET` con el nuevo valor y
   redeploy.
4. Validar que un evento de prueba del webhook se procese correctamente (firma
   verificada).

> Nota: en sandbox la validación de firma puede estar deshabilitada
> (`SKIP_WEBHOOK_SIGNATURE_VALIDATION=true`). La rotación de la firma solo
> tiene efecto real con la validación activa (producción).

### R2_ACCESS_KEY / R2_SECRET_KEY (Cloudflare R2)

Estas dos siempre se rotan juntas (un token R2 emite ambas).

1. En Cloudflare (R2 → Manage R2 API Tokens), crear un **nuevo** API token con
   los mismos permisos que el actual. Esto genera un nuevo par
   Access Key ID / Secret Access Key.
2. En Render, actualizar `R2_ACCESS_KEY` y `R2_SECRET_KEY` con el nuevo par y
   redeploy.
3. Verificar que upload/lectura de imágenes funciona con las credenciales
   nuevas.
4. Recién entonces, en Cloudflare, **revocar/eliminar** el token anterior.

### VAPID_PRIVATE_KEY / VAPID_PUBLIC_KEY (Web Push)

> **Advertencia.** Rotar las claves VAPID **invalida todas las suscripciones
> push existentes**. Las suscripciones de los navegadores están atadas al par
> de claves anterior; tras la rotación dejarán de recibir notificaciones y los
> clientes **deben re-suscribirse** (re-otorgar el permiso / re-registrar la
> suscripción). No hay grace period posible para VAPID.

1. Generar un nuevo par de claves VAPID (por ejemplo con
   `npx web-push generate-vapid-keys`).
2. En Render, actualizar `VAPID_PUBLIC_KEY` y `VAPID_PRIVATE_KEY` con el nuevo
   par y redeploy. Actualizar también la clave pública donde el frontend la
   consuma.
3. Asumir que todas las suscripciones previas quedan inválidas: el flujo de
   re-suscripción del cliente debe volver a registrar a cada usuario.
4. Opcional: limpiar las `push_subscription` viejas que ya no recibirán envíos.

---

## Flujo de trabajo

```bash
# Arrancar una nueva historia
git checkout main
git pull origin main
git checkout -b feature/US-XX-XX-descripcion

# Al terminar
git add .
git commit -m "feat: descripción (US-XX-XX)"
git push origin feature/US-XX-XX-descripcion
# Abrir PR en GitHub → CI corre automáticamente → merge si pasa

# Al mergear, se borra la rama vieja y se reanuda el ciclo
git checkout main
git branch -d feature/US-XX-XX-descripcion
```

### Convención de commits

```
feat:     nueva funcionalidad
fix:      corrección de bug
chore:    configuración, dependencias, refactor sin cambio funcional
test:     agregado o corrección de tests
docs:     documentación
refactor: refactor de código sin cambio funcional
```

---

## Migraciones de base de datos

El historial de migraciones vive en `backend/src/main/resources/db/migration/`
— Flyway es la única fuente de verdad. Cada cambio de esquema es una migración
versionada nueva (`V{n}__descripcion.sql`).

**Regla estricta:** nunca usar `ddl-auto=create` ni `ddl-auto=update`. Todo cambio de esquema va en una migración Flyway versionada.

---

## Módulos del sistema

### Subsistema Cliente (PWA)

- Selección de sucursal
- Consulta de menú por categorías
- Armado de pedido y carrito
- Pago vía MercadoPago o efectivo
- Seguimiento del pedido en tiempo real
- Notificaciones Web Push del estado del pedido

### Subsistema Backoffice

- Visualización y gestión de pedidos de la sucursal
- Avance del estado operativo
- Notificaciones en tiempo real (SSE)
- Gestión de catálogo (productos, categorías)
- Apertura/cierre de turnos y cierre de caja por sucursal (MANAGER)
- Selección de sucursal activa (ADMIN) vía `X-Branch-Id`

### Backend

- API REST documentada con OpenAPI
- Autenticación JWT por rol y sucursal, con refresh tokens
- Integración con MercadoPago vía Adapter pattern
- Almacenamiento multimedia en Cloudflare R2
- Cache de menú con Caffeine (Spring Cache)
- Migraciones de esquema con Flyway

---

## Estados del pedido

```
PENDING_PAYMENT → RECEIVED → IN_PREPARATION → ON_THE_WAY → DELIVERED
                                           ↘ READY (TAKEAWAY) ↗
PENDING_PAYMENT → CANCELLED
RECEIVED → CANCELLED
IN_PREPARATION → CANCELLATION_REQUESTED → CANCELLED
                                       → IN_PREPARATION (rechazado)
```

---

## Documentación

- **Backlog completo**: `docs/BACKLOG.md`
- **Deuda técnica activa**: `docs/DEUDA_TECNICA.md`
- **Flags de desarrollo / auditoría**: `docs/AUDIT_DEV_FLAGS.md`
- **Paleta de colores**: `docs/COLORS.md`
- **Informe técnico**: `docs/informe_tecnico.md`
- **API (local)**: `http://localhost:8080/swagger-ui.html`

---

## Licencia

Proyecto privado — todos los derechos reservados.
