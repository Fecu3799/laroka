import { useState, useEffect } from "react";

function decodeToken(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    if (typeof payload.exp !== "number") return null;
    if (payload.exp * 1000 <= Date.now()) return "expired";
    return payload;
  } catch {
    return null;
  }
}

export default function useAuth() {
  const [rawToken, setRawToken] = useState(() =>
    localStorage.getItem("laroka_token"),
  );
  const [auth, setAuth] = useState(() => {
    const token = localStorage.getItem("laroka_token");
    return token ? decodeToken(token) : null;
  });

  useEffect(() => {
    // Re-lee el token desde localStorage. El evento "storage" solo dispara en OTRAS
    // pestañas, así que el login/logout en la pestaña actual emite el evento custom
    // "laroka:token-changed" para que useAuth se sincronice sin necesitar un refresh.
    function syncFromStorage() {
      const token = localStorage.getItem("laroka_token");
      setRawToken(token);
      setAuth(token ? decodeToken(token) : null);
    }
    function handleStorage(e) {
      if (e.key === "laroka_token") syncFromStorage();
    }
    window.addEventListener("storage", handleStorage);
    window.addEventListener("laroka:token-changed", syncFromStorage);
    return () => {
      window.removeEventListener("storage", handleStorage);
      window.removeEventListener("laroka:token-changed", syncFromStorage);
    };
  }, []);

  const isExpired = auth === "expired";
  const payload = auth !== null && auth !== "expired" ? auth : null;

  return {
    token: rawToken,
    userId: payload?.sub ?? payload?.userId ?? null,
    role: payload?.role ?? null,
    branchId: payload?.branchId ?? null,
    branchName: payload?.branchName ?? null,
    tenantId: payload?.tenantId ?? null,
    tenantName: payload?.tenantName ?? null,
    isAuthenticated: payload !== null,
    isExpired,
  };
}
