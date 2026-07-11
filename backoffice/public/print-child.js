/**
 * Bootstrap de impresión de la ventana hija de ticket/comanda (US-16B-05).
 *
 * Vive como archivo estático servido same-origin (no inline) a propósito: la CSP
 * del backoffice usa `script-src 'self'` sin `unsafe-inline`, y la ventana hija
 * (un documento blob:) hereda esa CSP, así que un `<script>` inline quedaría
 * bloqueado. Un script externo del propio origen sí está permitido por `'self'`.
 *
 * Corre en el hilo de ESTA ventana hija, que además está en un proceso de
 * renderizado separado (la abrimos con `noopener`): por eso el diálogo nativo de
 * impresión, que es modal a nivel de proceso, no congela la pestaña del
 * backoffice que la abrió. print() y close() nunca se llaman desde el opener.
 *
 * onafterprint no dispara de forma consistente al cancelar el diálogo en todos
 * los navegadores; el botón "Cerrar" (#ticket-close) es el fallback para cerrar
 * la ventana a mano en ese caso.
 */
(function () {
  window.onafterprint = function () { window.close(); };

  var closeBtn = document.getElementById('ticket-close');
  if (closeBtn) {
    closeBtn.addEventListener('click', function () { window.close(); });
  }

  // onload asegura que el layout esté renderizado antes de abrir el diálogo.
  window.onload = function () { window.print(); };
})();
