/**
 * Aplicación temprana del tema persistido (US-15-F-04).
 *
 * Vive como archivo estático servido same-origin (no inline) a propósito, mismo
 * patrón que print-child.js: la CSP del backoffice usa `script-src 'self'` sin
 * `unsafe-inline`, así que un `<script>` inline en index.html quedaría bloqueado
 * en producción. Un script externo del propio origen sí está permitido por
 * `'self'`.
 *
 * Se referencia con `<script src>` sincrónico en el <head> (sin defer/async):
 * corre antes del primer render y evita el flash de tema incorrecto. Default:
 * dark (paleta actual).
 */
(function () {
  try {
    var t = localStorage.getItem('pedisur_backoffice_theme');
    document.documentElement.setAttribute('data-theme', t === 'light' ? 'light' : 'dark');
  } catch {
    document.documentElement.setAttribute('data-theme', 'dark');
  }
})();
