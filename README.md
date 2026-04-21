# LaRoka — Sistema de Gestión de Pedidos
 
Sistema de gestión digital de pedidos para pizzería multi-sucursal, concebido como base evolutiva hacia una plataforma multi-comercio.
 
---
 
## Descripción
 
LaRoka digitaliza y centraliza el flujo de pedidos entre clientes y sucursales, cubriendo desde la selección del menú hasta el seguimiento operativo en tiempo real. El sistema está compuesto por una aplicación cliente (PWA mobile-first), un backoffice para el personal del local y un backend centralizado con lógica de negocio.
 
---
 
## Stack Tecnológico
 
| Componente | Tecnología |
|---|---|
| Backend | Java 21 + Spring Boot 3.4 |
| Base de datos | PostgreSQL 16 |
| Migraciones | Flyway |
| Frontend cliente | React + Vite (PWA) |
| Frontend backoffice | React + Vite |
| Storage multimedia | Cloudflare R2 |
| Proveedor de pagos | MercadoPago |
| Hosting backend | Render |
| Hosting frontends | Vercel |
| CI | GitHub Actions |
 
---
 
## Arquitectura
 
Monolito modular. El backend centraliza toda la lógica de negocio organizado en módulos independientes con separación explícita de capas.
 
```
laroka/
├── backend/                  # Spring Boot — API REST
│   └── src/main/java/com/laroka/backend/
│       ├── auth/             # Autenticación JWT
│       ├── branch/           # Sucursales
│       ├── catalog/          # Productos y categorías
│       ├── notification/     # Eventos SSE al backoffice
│       ├── order/            # Ciclo de vida del pedido
│       ├── payment/          # Integración MercadoPago
│       ├── pizzeria/         # Entidad raíz multi-pizzería
│       ├── shared/           # Excepciones y utilidades comunes
│       └── staffuser/        # Usuarios internos
├── client/                   # React PWA — Interfaz del cliente
├── backoffice/               # React — Interfaz del personal
└── docs/                     # Documentación del proyecto
    ├── BACKLOG.md
    ├── DEUDA_TECNICA.md
    └── informe_tecnico.pdf
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
git clone https://github.com/tu-usuario/laroka.git
cd laroka
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
    url: jdbc:postgresql://localhost:5432/laroka
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
DB_URL=jdbc:postgresql://localhost:5432/laroka
DB_USER=postgres
DB_PASS=postgres
 
# Cloudflare R2
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_BUCKET_NAME=laroka-dev
R2_ENDPOINT=
 
# MercadoPago
MERCADOPAGO_KEY=
MERCADOPAGO_WEBHOOK_SECRET=
 
# JWT
JWT_SECRET=
JWT_EXPIRATION=28800000
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
 
El proyecto tiene tres workflows de GitHub Actions:
 
| Workflow | Trigger | Steps |
|---|---|---|
| ci-backend.yml | PR a main con cambios en backend/ | build → linting (Checkstyle) → tests |
| ci-client.yml | PR a main con cambios en client/ | build → linting (ESLint) |
| ci-backoffice.yml | PR a main con cambios en backoffice/ | build → linting (ESLint) |
 
Todo merge a main requiere PR con CI en verde.

CD se implementa al terminar el desarrollo del MVP
 
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
 
| Versión | Contenido |
|---|---|
| V1 | pizzeria |
| V2 | branch |
| V3 | category |
| V4 | product |
| V5 | staff_user |
| V6 | order + order_item |
| V7 | order_status_history |
| V8 | delivery_address (columna en order) |
| V9 | payment |
| V10 | branch operating hours |
 
**Regla estricta:** nunca usar `ddl-auto=create` ni `ddl-auto=update`. Todo cambio de esquema va en una migración Flyway versionada.
 
---
 
## Módulos del sistema
 
### Subsistema Cliente (PWA)
- Selección de sucursal
- Consulta de menú por categorías
- Armado de pedido y carrito
- Pago vía MercadoPago o efectivo
- Seguimiento del pedido en tiempo real
### Subsistema Backoffice
- Visualización y gestión de pedidos de la sucursal
- Avance del estado operativo
- Notificaciones en tiempo real (SSE)
- Gestión de catálogo (productos, categorías)
### Backend
- API REST documentada con OpenAPI
- Autenticación JWT por rol y sucursal
- Integración con MercadoPago vía Adapter pattern
- Almacenamiento multimedia en Cloudflare R2
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
- **Informe técnico**: `docs/informe_tecnico.pdf`
- **API (local)**: `http://localhost:8080/swagger-ui.html`

---
 
## Licencia
 
Proyecto privado — todos los derechos reservados.