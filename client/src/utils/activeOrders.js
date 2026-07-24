const STORAGE_KEY = "pedisur_active_orders";

export function readActiveOrders() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return parsed.map((e) =>
      typeof e === "object" && e && e.orderId
        ? e
        : { orderId: e, branchId: null },
    );
  } catch {
    return [];
  }
}

export function addActiveOrder(orderId, branchId) {
  try {
    const current = readActiveOrders();
    const exists = current.some((e) => e.orderId === orderId);
    if (!exists) {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify([...current, { orderId, branchId }]),
      );
      window.dispatchEvent(new Event("pedisur_orders_updated"));
    }
  } catch {
    /* storage unavailable */
  }
}

export function removeActiveOrder(orderId) {
  try {
    const current = readActiveOrders();
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify(current.filter((e) => e.orderId !== orderId)),
    );
    window.dispatchEvent(new Event("pedisur_orders_updated"));
  } catch {
    /* storage unavailable */
  }
}
