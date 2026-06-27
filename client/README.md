# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## Variables de entorno

Las variables se inyectan en tiempo de build. En desarrollo van en `.env.local`
y en producción se configuran en el proyecto de Vercel. Ver `.env.example` para
la lista completa. El prefijo `VITE_` es obligatorio para que Vite las exponga
vía `import.meta.env` en el navegador.

| Variable | Obligatoria | Descripción |
| --- | --- | --- |
| `VITE_TENANT_ID` | Sí | Identificador (Integer) del tenant que sirve esta PWA. Se usa para filtrar las sucursales mediante `GET /branches?tenantId=${VITE_TENANT_ID}`. Si no está definida (ausente o vacía), la app muestra una pantalla de error de configuración y **no** realiza ninguna llamada al backend. Configurar en Vercel y en `.env.local`. |
| `VITE_API_URL` | No | URL base del backend. Por defecto `http://localhost:8080`. |
| `VITE_APP_URL` | No | URL pública de la PWA, usada para los redirects de pago. Por defecto `http://localhost:5173`. |
| `VITE_VAPID_PUBLIC_KEY` | No | Clave pública VAPID para Web Push (pareja de la privada del backend). |
| `VITE_DEV_MP_DEBUG_LOGS` | No | `true` habilita logs `[MP-DEBUG]` de MercadoPago en consola. |

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
