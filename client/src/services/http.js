function dispatchToast(message) {
  window.dispatchEvent(
    new CustomEvent("laroka:toast", { detail: { message } }),
  );
}

export async function apiFetch(url, options = {}) {
  let res;
  try {
    res = await fetch(url, options);
  } catch {
    dispatchToast("Sin conexión. Verificá tu internet.");
    throw new Error("network_error");
  }

  if (res.ok) return res;

  let body = null;
  try {
    body = await res.json();
  } catch {
    /* empty */
  }
  const message = body?.message ?? null;

  if (res.status >= 500) {
    dispatchToast("Ocurrió un error. Intentá de nuevo.");
  } else {
    dispatchToast(message ?? "Error al procesar la solicitud.");
  }

  const err = new Error(message ?? `HTTP ${res.status}`);
  err.status = res.status;
  // Body estructurado del error disponible para el caller (p. ej. productId en
  // el 422 de producto no disponible, US-15-CF-05) — evita parsear el mensaje.
  err.body = body;
  throw err;
}
