# Known Issues — Backoffice

## US-SEC-02 — Content Security Policy

Se aplicó una CSP estricta a todas las rutas (`/(.*)`) vía `vercel.json`:

```
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';
img-src 'self' data: blob: *.r2.cloudflarestorage.com;
connect-src 'self' https://laroka-backend.onrender.com;
frame-ancestors 'none'
```

### Ajuste: Google Fonts self-hosted

**Conflicto detectado:** el backoffice cargaba las familias Barlow Condensed,
Inter y JetBrains Mono vía `@import url('https://fonts.googleapis.com/...')` en
`src/components/Layout.css` y `src/pages/Login.css`. Con la CSP de arriba:

- La hoja de estilos de `fonts.googleapis.com` la gobierna `style-src`, que solo
  permite `'self' 'unsafe-inline'` → bloqueada.
- Los archivos de fuente de `fonts.gstatic.com` necesitan `font-src`; al no estar
  declarada, cae a `default-src 'self'` → bloqueados.

Resultado: violaciones de CSP en consola y tipografía caída a fuentes del sistema.

**Resolución (no es deuda, es la decisión tomada):** en vez de relajar la CSP
agregando `fonts.googleapis.com` / `fonts.gstatic.com`, se **self-hostean** las
fuentes con `@fontsource` (paquetes `@fontsource/inter`,
`@fontsource/barlow-condensed`, `@fontsource/jetbrains-mono`). Los `.woff2` se
empaquetan en el build y se sirven same-origin desde `/assets`, por lo que
quedan cubiertos por `'self'` sin necesidad de orígenes externos.

- Imports de pesos centralizados en `src/main.jsx` (mismos pesos que usaba el
  `@import` original: Barlow Condensed 600/700/800, Inter 400/500/600/700,
  JetBrains Mono 400/700/800).
- Se eliminaron los `@import` remotos de `Layout.css` y `Login.css`.

**Justificación:** mantiene la CSP lo más estricta posible (sin abrir `style-src`
ni `font-src` a terceros), elimina la dependencia de runtime con Google Fonts y
conserva la tipografía idéntica.

### Verificación manual pendiente

La verificación en staging la realiza el desarrollador (este entorno no tiene
Vercel CLI autenticado ni navegador para inspeccionar DevTools):

1. **Header presente:** `curl -sI https://<preview-url>/ | grep -i content-security-policy`
   debe devolver el header `Content-Security-Policy` con el valor de arriba.
2. **Sin errores de CSP en consola** ejercitando: login, listado de pedidos,
   stream SSE (`/backoffice/events`), y cambio de estado de un pedido.
   - `connect-src` ya incluye `https://laroka-backend.onrender.com` (backend de
     producción que consume el SSE y la API). Si staging apunta a otro backend,
     ajustar `connect-src` con esa URL.
