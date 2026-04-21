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