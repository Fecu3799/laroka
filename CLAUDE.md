# LaRoka — Contexto del Proyecto

## Descripción

Sistema de gestión de pedidos para pizzería multi-sucursal.
Monolito modular con proyección a plataforma multi-pizzería.

## Stack

- Backend: Java Spring Boot (monolito modular)
- Frontend cliente: React + Vite (PWA) — Vercel
- Frontend backoffice: React + Vite — Vercel
- Base de datos: PostgreSQL (Render)
- Storage: Cloudflare R2 (compatible S3)
- Pagos: MercadoPago (adapter pattern)
- Migraciones: Flyway
- CI: GitHub Actions

## Estructura del repo

laroka/
backend/
client/
backoffice/
CLAUDE.md
README.md

## Arquitectura backend

Monolito modular. Módulos: branch, catalog, order,
payment, auth, staffuser, notification, pizzeria.

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

## Convenciones Frontend — Backoffice

### Estructura de carpetas obligatoria
src/
├── assets/
├── components/   # Componentes reutilizables (Layout, ProtectedRoute, etc)
├── hooks/        # Hooks reutilizables — un archivo por hook
├── pages/        # Una carpeta o archivo por página
├── services/     # Llamadas a la API — un archivo por módulo
└── index.css     # Variables CSS globales — única fuente de verdad

### Hooks
- Toda lógica reutilizable que use estado o efectos va en un hook en src/hooks/
- Nunca implementar lógica de negocio inline en un componente si se va a usar 
  en más de un lugar
- Hooks existentes obligatorios:
  - useAuth.js — única fuente de verdad para leer y decodificar el JWT. 
    Ningún componente lee laroka_token directamente ni decodifica el payload 
    por su cuenta. Siempre importar useAuth.

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
- Un archivo por módulo: authService.js, ordersService.js, etc.
- El token se obtiene desde useAuth, no desde localStorage directamente

### Reglas generales
- Nunca hardcodear strings de negocio ("LaRoka", "Puerto Madryn") en 
  componentes — siempre vienen del JWT vía useAuth o de la API
- Nunca usar ddl-auto=create o update (aplica al backend)
- Todo cambio de esquema de base de datos va en una migración Flyway versionada
- Antes de crear un componente nuevo verificar si ya existe uno reutilizable 
  en components/ que cubra el caso

## Cómo trabajar con este repo

- El backlog completo está en docs/BACKLOG.md
- El informe completo del proyecto está en docs/informe_tecnico.txt
- Cargar skills desde la raiz del repo (.claude/skills)
- Cada módulo tiene su CONTEXT.md con reglas específicas
- Al implementar una historia, referenciar su ID (ej: US-03-02)
- Un prompt por capa, validar antes de continuar

## Reglas para ClaudeCode

- No hacer commits automáticos
- No ejecutar git add ni git commit
- El desarrollador gestiona todo el versionado
- No ejecutar compilaciones ni validaciones intermedias
- No hacer verificaciones despues de cada archivo
- Implementar la tarea completa y reportar resultados al final

## Deudas técnicas activas

- Leer docs/DEUDA_TECNICA.md al inicio de cada sesión
- **DT-01**: Spring Security deshabilitado (todos los endpoints son públicos). Al trabajar en Sprint 2, alertar que DT-01 debe resolverse antes de continuar (US-02-03).

## Estilo de respuesta

- Responder directo, sin openers ("Claro!", "Excelente pregunta")
- Sin cierres innecesarios ("Espero que ayude")
- No reafirmar lo que el usuario dijo antes de responder
- No tocar código fuera del scope de la tarea
- No leer el mismo archivo dos veces en la misma sesión
- Si algo es incierto, decir "no sé" en lugar de inventar
