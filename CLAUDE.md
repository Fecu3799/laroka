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

## Cómo trabajar con este repo

- El backlog completo está en docs/BACKLOG.md
- El informe completo del proyecto está en docs/informe_tecnico.txt
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
