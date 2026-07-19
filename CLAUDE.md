# LaRoka — Contexto del Proyecto

## Descripción

Sistema de gestión de pedidos para pizzería multi-sucursal.
Monolito modular con proyección a plataforma multi-tenant (multi-pizzería).

## Stack

- Backend: Java 21 + Spring Boot 3 (monolito modular)
- Frontend cliente: React + Vite (PWA) — Vercel
- Frontend backoffice: React + Vite — Vercel
- Base de datos: PostgreSQL (Render)
- Storage: Cloudflare R2 (compatible S3)
- Pagos: MercadoPago (adapter pattern)
- Migraciones: Flyway
- Cache: Caffeine (Spring Cache)
- CI/CD: GitHub Actions
- Monitoreo: New Relic

## Estructura del repo

laroka/
├── backend/
├── client/
├── backoffice/
├── CLAUDE.md
└── README.md

## Arquitectura backend

Monolito modular. Módulos: branch, catalog, order,
payment, auth, staffuser, notification, shift, pizzeria.

Capas obligatorias por módulo:

- Controller: solo expone endpoints, sin lógica
- Service: toda la lógica de negocio
- Repository: Spring Data JPA únicamente
- DTO: nunca exponer entidades directamente
- Mapper: conversión entidad ↔ DTO
- Exception: excepciones tipadas por módulo

## Reglas de arquitectura

- ddl-auto=validate siempre, nunca create ni update
- Todo cambio de esquema = migración Flyway versionada
- Integraciones externas siempre via interfaz + Adapter
- Errores manejados por GlobalExceptionHandler únicamente
- Credenciales siempre por variables de entorno, nunca hardcodeadas
- DTOs siempre separados: XxxRequestDTO para entrada, XxxResponseDTO para salida
- RequestDTO: campos de entrada sin id ni timestamps
- ResponseDTO: campos de salida incluyendo id y timestamps
- El Mapper convierte DTO → Entidad en el Controller
- El Service recibe y retorna Entidades, nunca DTOs
- El Controller convierte Entidad → ResponseDTO antes de retornar

## Migraciones Flyway — tabla de versiones

| Versión | Contenido                                  |
|---------|--------------------------------------------|
| V1      | tenant                                     |
| V2      | branch                                     |
| V3      | category                                   |
| V4      | product + branch_product                   |
| V5      | staff_user                                 |
| V6      | order + order_item (incluye campo origin)  |
| V7      | order_status_history                       |
| V8      | delivery_address (columna en order)        |
| V9      | payment                                    |
| V10     | branch operating hours                     |
| V11     | branch_qr                                  |
| V12     | cancellation_reason en order_status_history |
| V13     | push_subscription                          |
| V14     | refresh_token                              |
| V15     | work_shift                                 |
| V16     | work_shift_summary                         |
| V17     | work_shift_summary analytics columns       |
| V18     | branch accepting_orders                    |
| V19     | order shift_id (FK work_shift)             |
| V20     | work_shift open unique index               |
| V21     | order shift_id NOT NULL                     |
| V22     | push_subscription + order push_subscription_id |
| V23     | staff_user active                          |
| V24     | branch max_shift_duration_minutes          |
| V25     | tenant email_domain                        |
| V26     | tenant_profile                             |
| V27     | branch_schedule + branch_schedule_override (drop branch opening_time/closing_time/open_days) |
| V28     | branch image_url                           |
| V29     | order order_number + branch_order_sequence |
| V30     | payment refunded_amount                    |
| V31     | category_type (tipos de categoría maestros) |
| V32     | category category_type_id (FK a category_type, nullable) |
| V33     | order_item second_product_id (FK a product, nullable) |
| V34     | category_type allows_sizes                  |
| V35     | product_size (tamaños con precio propio por producto) |

Toda migración nueva debe ser la siguiente versión disponible en esta tabla.
Actualizar esta tabla al agregar una migración.

## Roles y autorización

Tres roles activos: ADMIN, MANAGER, STAFF.

- ADMIN: dueño del tenant. JWT sin branchId. Envía X-Branch-Id en cada request
  de backoffice. Puede operar sobre cualquier sucursal del tenant.
- MANAGER: encargado de sucursal. JWT con branchId. Puede abrir/cerrar turnos,
  ver resúmenes y cierre de caja de su sucursal.
- STAFF: empleado operativo. JWT con branchId. Solo gestión de pedidos.

Resolución de branchId centralizada en SecurityUtils.resolveBranchId(principal, request):
- STAFF / MANAGER → lee branchId del token
- ADMIN → lee header X-Branch-Id, lanza 400 si ausente o inválido, 403 si
  el branchId no pertenece al tenant del token

Roles futuros planificados (no implementar hasta el sprint correspondiente):
KITCHEN, RIDER. Se agregarán al enum cuando exista el actor concreto.

## Convenciones de código

- camelCase: variables y métodos
- PascalCase: clases
- snake_case: columnas de base de datos
- Conventional Commits: feat / fix / chore / refactor / test / docs

## Reglas de negocio críticas

- Un pedido solo existe con pago aprobado (RN-01)
- pizzeria_id presente en todas las entidades de dominio
- Personal solo ve pedidos de su propia sucursal (RN-11)
- Precio congelado al momento del pedido (RN-05)
- Todo cambio de estado registrado con timestamp (RN-12)
- Estados terminales no reversibles (RN-13)
- Un turno es por sucursal, no por empleado (RN-TU-01)
- El cierre de turno persiste WorkShiftSummary inmutablemente (RN-TU-02)
- Abrir turno con otro ya abierto cierra el anterior automáticamente (RN-TU-03)

## Convenciones Frontend — Backoffice

### Estructura de carpetas obligatoria
src/
├── assets/
├── components/   # Componentes reutilizables (Layout, ProtectedRoute, etc)
├── hooks/        # Hooks reutilizables — un archivo por hook
├── pages/        # Una carpeta o archivo por página
├── services/     # Llamadas a la API — un archivo por módulo
└── index.css     # Variables CSS globales — única fuente de verdad

### Hooks obligatorios
- useAuth.js — única fuente de verdad para leer y decodificar el JWT.
  Ningún componente lee laroka_token directamente ni decodifica el payload
  por su cuenta. Siempre importar useAuth.
- useBranch.js — única fuente de verdad para activeBranchId y activeBranchName.
  Consume BranchProvider. Ningún componente lee activeBranchId de otro lado.

### Estado de sucursal activa (ADMIN)
- BranchProvider envuelve la app en App.jsx
- activeBranchId y activeBranchName se persisten en sessionStorage bajo
  laroka_active_branch. Se limpian en logout.
- Toda request de backoffice incluye X-Branch-Id via backofficeHeaders()
  centralizado en ordersService.js cuando activeBranchId != null.
- ADMIN: redirige a /branch-select post-login. /branch-select es solo
  accesible para role=ADMIN — guard sincrónico con <Navigate>.
- Al cambiar de sucursal: clearOrders() + setSelectedId(null) antes del
  re-fetch para evitar mezcla de datos entre sucursales.

### Variables CSS
- --sidebar-width definido en index.css es la única fuente de verdad para el
  ancho del sidebar. Nunca hardcodear ese valor en otro archivo.
- Todos los colores de la paleta definidos como variables en index.css.
  Nunca hardcodear hex en componentes.
- Variables de paleta obligatorias:
  --color-bg: #030d04
  --color-accent: #f5c518
  --color-green-primary: #00c853
  --color-sidebar-bg: #030d04

### Servicios
- Toda llamada a la API va en src/services/ — nunca fetch/axios inline en
  un componente o hook
- Un archivo por módulo: authService.js, ordersService.js, shiftsService.js, etc.
- El token se obtiene desde useAuth, no desde localStorage directamente
- backofficeHeaders(token, branchId, extra) centraliza Authorization +
  X-Branch-Id. Usar en toda llamada de backoffice.

### SSE
- La conexión SSE en Layout.jsx incluye X-Branch-Id en el request.
- Se reconecta automáticamente cuando activeBranchId cambia.

### Reglas generales
- Nunca hardcodear strings de negocio ("LaRoka", "Puerto Madryn") en
  componentes — siempre vienen del JWT vía useAuth o de la API
- Nunca usar ddl-auto=create o update (aplica al backend)
- Todo cambio de esquema de base de datos va en una migración Flyway versionada
- Antes de crear un componente nuevo verificar si ya existe uno reutilizable
  en components/ que cubra el caso

## Known issues activos

- Webhook signature validation de MercadoPago deshabilitada en sandbox
  (SKIP_WEBHOOK_SIGNATURE_VALIDATION=true). Comportamiento aceptado — sandbox
  no soporta validación de firma.
- JWT blacklist in-memory no persiste entre reinicios del servidor. Tokens
  invalidados por logout vuelven a ser válidos tras un redeploy. Aceptado
  para el contexto actual.
- Rate limiter Caffeine in-memory: no funciona correctamente en escenarios
  multi-instancia. No aplica hoy (Render Starter = 1 instancia). Documentado
  como limitación conocida.
- SSE selective update (US-MT-02) tiene historial de stale closures — área
  frágil. No modificar sin tests previos.
- CSP del client (PWA): la CSP vive en `client/vercel.json` (header HTTP), no
  en un `<meta>`. El Service Worker liga su CSP al instalarse y hace `fetch()`
  de imágenes (CacheFirst) sujeto a esa CSP. Como `vercel.json` no entra al
  build, cambiar la CSP deja el `sw.js` compilado byte-idéntico y los clientes
  con SW ya instalado NO toman la CSP nueva. **Regla: al cambiar la CSP en
  `client/vercel.json`, bumpear `SW_VERSION` en `client/src/sw.js`** (cualquier
  valor distinto) para forzar la reinstalación del SW. Las imágenes de R2 se
  sirven desde `*.r2.dev`, presente en `img-src` y `connect-src` (este último
  porque el SW las trae por `fetch()`).

## Cómo trabajar con este repo

- El backlog completo está en docs/BACKLOG.md
- El informe técnico está en docs/informe_tecnico.txt
- Cargar skills desde la raíz del repo (.claude/skills)
- Cada módulo tiene su CONTEXT.md con reglas específicas
- Al implementar una historia, referenciar su ID (ej: US-03-02)
- Un prompt por historia de usuario, validar antes de continuar

## Reglas para Claude Code

- No utilizar git
- El desarrollador gestiona todo el versionado
- Implementar la tarea completa y reportar resultados al final
- No especular sobre código que no se ha leído — usar Read si hay dudas
- Si algo es incierto, decir "no sé" en lugar de inventar

## Deudas técnicas activas

- Leer docs/DEUDA_TECNICA.md al inicio de cada sesión