import { useState, useEffect, useCallback } from "react";
import { useOutletContext } from "react-router-dom";
import useAuth from "../hooks/useAuth";
import useBranch from "../hooks/useBranch";
import useCurrentShift from "../hooks/useCurrentShift";
import useOrders from "../hooks/useOrders";
import useOrderDetail from "../hooks/useOrderDetail";
import {
  advanceOrderStatus,
  resolveCancelRequest,
} from "../services/ordersService";
import { apiFetch } from "../services/http";
import {
  STATUS_CONFIG,
  STATUS_PRIORITY,
  sortOrders,
  getNextStatus,
  canGoBack as goBackAllowed,
  canCancel as cancelAllowed,
} from "../utils/ordersUtils";
import "./Orders.css";

const API_URL = import.meta.env.VITE_API_URL ?? "";

// ── Constants ─────────────────────────────────────────────────

const TERMINAL = new Set(["DELIVERED", "CANCELLED"]);

const PAYMENT_STATUS_LABEL = {
  APPROVED: "Pagado",
  PENDING: "Pendiente",
  REJECTED: "Rechazado",
  CANCELLED: "Cancelado",
};

const PAYMENT_STATUS_COLOR = {
  APPROVED: "#4ade80",
  PENDING: "#fb923c",
  REJECTED: "#f87171",
  CANCELLED: "#f87171",
};

const PAYMENT_METHOD_LABEL = {
  MERCADOPAGO: "MercadoPago",
  CASH: "Efectivo",
};

const PAYMENT_BADGE_CONFIG = {
  PENDING: { bg: "#2d1400", color: "#fb923c", border: "#5c2a00" },
  APPROVED: { bg: "#0a2e14", color: "#4ade80", border: "#1a5c2c" },
  REJECTED: { bg: "#2e0f0f", color: "#f87171", border: "#5c1f1f" },
  CANCELLED: { bg: "#2e0f0f", color: "#f87171", border: "#5c1f1f" },
};

const TABS = [
  { key: "ALL", label: "Todos" },
  { key: "RECEIVED", label: "Recibidos" },
  { key: "IN_PREPARATION", label: "En preparación" },
  { key: "ON_THE_WAY", label: "En camino" },
  { key: "DELIVERED", label: "Entregados" },
  { key: "CANCELLED", label: "Cancelados" },
];

const STATUS_CHIPS = [
  {
    key: "RECEIVED",
    label: "Recibidos",
    bg: "#1d3557",
    color: "#90bdf9",
    border: "#2a4a80",
  },
  {
    key: "IN_PREPARATION",
    label: "En prep.",
    bg: "#2d1f00",
    color: "#fbbf24",
    border: "#5c3d00",
  },
  {
    key: "ON_THE_WAY",
    label: "En camino",
    bg: "#2d1047",
    color: "#c084fc",
    border: "#5a1f8a",
  },
  {
    key: "DELIVERED",
    label: "Entregados",
    bg: "#0a2e14",
    color: "#4ade80",
    border: "#1a5c2c",
  },
];

const SEQUENCE_DELIVERY = [
  "RECEIVED",
  "IN_PREPARATION",
  "ON_THE_WAY",
  "DELIVERED",
];
const SEQUENCE_TAKEAWAY = [
  "RECEIVED",
  "IN_PREPARATION",
  "READY_FOR_PICKUP",
  "DELIVERED",
];

// ── Helpers ───────────────────────────────────────────────────

function shortId(id) {
  return "#" + id.replace(/-/g, "").slice(0, 4).toUpperCase();
}

function formatTime(createdAt) {
  if (!createdAt) return "—";
  const date = new Date(createdAt);
  const now = new Date();
  const timeStr = date.toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  if (date.toDateString() === now.toDateString()) return `Hoy · ${timeStr}`;
  return (
    date.toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit" }) +
    ` · ${timeStr}`
  );
}

function formatHour(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function formatDateTime(ts) {
  if (!ts) return "—";
  const date = new Date(ts);
  const day = date.toLocaleDateString("es-AR", { weekday: "short" });
  const time = date.toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  return `${day} ${time}`;
}

function formatPrice(n) {
  if (n == null) return "—";
  return "$" + Number(n).toLocaleString("es-AR", { maximumFractionDigits: 0 });
}

function getInitials(name) {
  if (!name) return "?";
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
}

function filterOrders(orders, activeTab, searchQuery, dismissedIds) {
  let list = orders;
  if (activeTab === "ALL") {
    list = list.filter((o) => !dismissedIds.has(o.id));
  } else if (activeTab === "ON_THE_WAY") {
    list = list.filter(
      (o) => o.status === "ON_THE_WAY" || o.status === "READY_FOR_PICKUP",
    );
  } else if (activeTab === "IN_PREPARATION") {
    list = list.filter(
      (o) =>
        o.status === "IN_PREPARATION" || o.status === "CANCELLATION_REQUESTED",
    );
  } else {
    list = list.filter((o) => o.status === activeTab);
  }
  if (searchQuery.trim()) {
    const q = searchQuery.toLowerCase();
    list = list.filter(
      (o) =>
        shortId(o.id).toLowerCase().includes(q) ||
        (o.customerName && o.customerName.toLowerCase().includes(q)),
    );
  }
  return list;
}

function tabCount(orders, key, dismissedIds) {
  if (key === "ALL")
    return orders.filter((o) => !dismissedIds.has(o.id)).length;
  if (key === "ON_THE_WAY")
    return orders.filter(
      (o) => o.status === "ON_THE_WAY" || o.status === "READY_FOR_PICKUP",
    ).length;
  if (key === "IN_PREPARATION")
    return orders.filter(
      (o) =>
        o.status === "IN_PREPARATION" || o.status === "CANCELLATION_REQUESTED",
    ).length;
  return orders.filter((o) => o.status === key).length;
}

// ── Icons ─────────────────────────────────────────────────────

function SearchIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="8" stroke="#7a9b80" strokeWidth="2" />
      <path
        d="m21 21-4.35-4.35"
        stroke="#7a9b80"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

function RefreshIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M1 4v6h6"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M3.51 15a9 9 0 1 0 .49-4.95"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
      />
    </svg>
  );
}

function PhoneIcon({ size = 11 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 1.27h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L7.91 8.91a16 16 0 0 0 6 6l.91-.91a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"
        stroke="#4a6b50"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function PinIcon({ size = 11 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"
        stroke="#4a6b50"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="10" r="3" stroke="#4a6b50" strokeWidth="2" />
    </svg>
  );
}

function ScooterIcon({ size = 11 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M3 11l1-4h8l2 4"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle
        cx="5.5"
        cy="15.5"
        r="2.5"
        stroke="currentColor"
        strokeWidth="1.8"
      />
      <circle
        cx="18.5"
        cy="15.5"
        r="2.5"
        stroke="currentColor"
        strokeWidth="1.8"
      />
      <path
        d="M14 7h4l2 4H9"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function StoreIcon({ size = 11 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <polyline
        points="9 22 9 12 15 12 15 22"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M18 6 6 18"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
      <path
        d="M6 6l12 12"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

function ArrowLeftIcon() {
  return (
    <svg
      width="13"
      height="13"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M19 12H5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
      <path
        d="M12 5l-7 7 7 7"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function XCancelIcon() {
  return (
    <svg
      width="13"
      height="13"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M18 6 6 18"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
      <path
        d="M6 6l12 12"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

function PaymentStatusIcon({ status }) {
  if (status === "APPROVED")
    return (
      <svg
        width="11"
        height="11"
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <polyline
          points="20 6 9 17 4 12"
          stroke="currentColor"
          strokeWidth="2.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );
  if (status === "REJECTED" || status === "CANCELLED")
    return (
      <svg
        width="11"
        height="11"
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M18 6 6 18"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
        <path
          d="M6 6l12 12"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
    );
  return (
    <svg
      width="11"
      height="11"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
      <polyline
        points="12 6 12 12 16 14"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

// ── Main component ────────────────────────────────────────────

export default function Orders() {
  const { token } = useAuth();
  const { activeBranchId: branchId } = useBranch();
  const { newOrderCount, cancelCount, resetCounts, setOpenOrderId } = useOutletContext();
  const { shift } = useCurrentShift();
  const {
    orders,
    loading,
    error,
    refresh,
    clearOrders,
    dismissOrder,
    dismissedIds,
    updateOrderInList,
    updatePaymentInList,
    replaceOrderInList,
    updateSingleOrder,
  } = useOrders(token, branchId, shift?.openedAt ?? null);

  const [activeTab, setActiveTab] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedId, setSelectedId] = useState(null);
  const [advancing, setAdvancing] = useState(null);
  const { detail, refetchDetail } = useOrderDetail(selectedId, token, branchId);
  // ── Refresh when a new order is created via the modal ────────
  useEffect(() => {
    function handleOrderCreated() {
      refresh();
    }
    window.addEventListener("laroka:order-created", handleOrderCreated);
    return () =>
      window.removeEventListener("laroka:order-created", handleOrderCreated);
  }, [refresh]);

  // ── Feed item pressed in SubHeader → merge order into list ───
  useEffect(() => {
    function handleOrderInsert(e) {
      if (e.detail?.order) updateSingleOrder(e.detail.order);
    }
    window.addEventListener("laroka:order-insert", handleOrderInsert);
    return () =>
      window.removeEventListener("laroka:order-insert", handleOrderInsert);
  }, [updateSingleOrder]);

  // ── Sync open panel ID to Layout ref ─────────────────────────
  useEffect(() => { setOpenOrderId(selectedId) }, [selectedId, setOpenOrderId])

  // ── SSE: actualiza lista en tiempo real; refresca detalle si el panel está abierto ─
  useEffect(() => {
    function handleOrderUpdated(e) {
      const { orderId, type, order } = e.detail;
      if (type === 'ORDER_UPDATED' && order) {
        replaceOrderInList(order);
        if (selectedId === orderId) refetchDetail();
      } else if (type === 'CANCELLATION_REQUESTED') {
        if (selectedId && orderId === selectedId) {
          updateOrderInList(orderId, 'CANCELLATION_REQUESTED');
          refetchDetail();
        }
      }
    }
    window.addEventListener("laroka:order-updated", handleOrderUpdated);
    return () =>
      window.removeEventListener("laroka:order-updated", handleOrderUpdated);
  }, [selectedId, refetchDetail, updateOrderInList, replaceOrderInList]);

  // ── Clear list and close panel synchronously on branch change ─
  useEffect(() => {
    clearOrders();
    setSelectedId(null);
  }, [branchId, clearOrders]);

  // ── Auto-clear selected if order leaves visible list ─────────
  useEffect(() => {
    if (selectedId && !orders.find((o) => o.id === selectedId)) {
      setSelectedId(null);
    }
  }, [selectedId, orders]);

  // ── handleAdvance ────────────────────────────────────────────
  const handleAdvance = useCallback(
    async (e, order) => {
      e.stopPropagation();
      const next = getNextStatus(order.status, order.orderType);
      if (!next) return;
      setAdvancing(order.id);
      try {
        await advanceOrderStatus(order.id, next, token, branchId);
        updateOrderInList(order.id, next);
        refetchDetail();
      } catch {
        /* silent */
      } finally {
        setAdvancing(null);
      }
    },
    [token, branchId, updateOrderInList, refetchDetail],
  );

  // ── Derived ──────────────────────────────────────────────────
  const chipCounts = {
    RECEIVED: orders.filter((o) => o.status === "RECEIVED").length,
    IN_PREPARATION: orders.filter((o) => o.status === "IN_PREPARATION").length,
    ON_THE_WAY: orders.filter(
      (o) => o.status === "ON_THE_WAY" || o.status === "READY_FOR_PICKUP",
    ).length,
    DELIVERED: orders.filter((o) => o.status === "DELIVERED").length,
  };
  const activeCount = orders.filter((o) => !TERMINAL.has(o.status)).length;
  const visibleOrders = sortOrders(
    filterOrders(orders, activeTab, searchQuery, dismissedIds),
  );
  const selectedOrder = orders.find((o) => o.id === selectedId) ?? null;
  const panelOrder =
    (detail?.id === selectedId ? detail : null) ?? selectedOrder;
  const panelOpen = selectedId !== null;
  const contracted = panelOpen;

  // ── Render ───────────────────────────────────────────────────
  return (
    <div className="orders-page">
      {/* ── Top bar ──────────────────────────────────────────── */}
      <div className="orders-header">
        <div className="orders-header-title-block">
          <h1 className="orders-title">Pedidos</h1>
          <span className="orders-subtitle">
            {orders.length} pedidos totales · {activeCount} activos
          </span>
        </div>

        <div className="orders-header-spacer" />

        <div className="orders-chips">
          {STATUS_CHIPS.map((chip) => (
            <div
              key={chip.key}
              className="orders-chip"
              style={{
                backgroundColor: chip.bg,
                color: chip.color,
                borderColor: chip.border,
              }}
            >
              <span className="orders-chip-count">
                {chipCounts[chip.key] ?? 0}
              </span>
              <span className="orders-chip-label">{chip.label}</span>
            </div>
          ))}
        </div>

        <div className="orders-search">
          <SearchIcon />
          <input
            className="orders-search-input"
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar #ID o cliente..."
          />
        </div>

        <button
          className={`orders-refresh-btn${(newOrderCount > 0 || cancelCount > 0) ? ' orders-refresh-btn--notify' : ''}`}
          type="button"
          onClick={() => { resetCounts(); refresh(); if (selectedId) refetchDetail(); }}
        >
          <RefreshIcon />
          Actualizar lista
          {(newOrderCount > 0 || cancelCount > 0) && (
            <span className="orders-refresh-badges">
              {newOrderCount > 0 && (
                <span className="orders-refresh-badge orders-refresh-badge--received">{newOrderCount}</span>
              )}
              {cancelCount > 0 && (
                <span className="orders-refresh-badge orders-refresh-badge--cancel">{cancelCount}</span>
              )}
            </span>
          )}
        </button>
      </div>

      {/* ── Tabs ─────────────────────────────────────────────── */}
      <div className="orders-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className={`orders-tab${activeTab === tab.key ? " orders-tab--active" : ""}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
            <span
              className={`orders-tab-pill${activeTab === tab.key ? " orders-tab-pill--active" : ""}`}
            >
              {tabCount(orders, tab.key, dismissedIds)}
            </span>
          </button>
        ))}
      </div>

      {/* ── Main area ────────────────────────────────────────── */}
      <div className="orders-layout">
        {loading ? (
          <div className="orders-state-center">
            <div className="orders-spinner" />
          </div>
        ) : error ? (
          <div className="orders-state-center orders-state-error">{error}</div>
        ) : (
          <>
            <div
              className="orders-list-col"
              style={panelOpen ? { width: "55%", flexShrink: 0 } : undefined}
            >
              <div
                className={`orders-table-head${contracted ? " orders-table-head--contracted" : ""}`}
              >
                {contracted ? (
                  <>
                    <div className="col-head col-head--order">Pedido</div>
                    <div className="col-head col-head--customer">Cliente</div>
                    <div className="col-head col-head--pago">Pago</div>
                    <div className="col-head col-head--notes">Notas</div>
                    <div className="col-head col-head--status">Estado</div>
                  </>
                ) : (
                  <>
                    <div className="col-head col-head--order">Pedido</div>
                    <div className="col-head col-head--customer">Cliente</div>
                    <div className="col-head col-head--items">Productos</div>
                    <div className="col-head col-head--pago">Pago</div>
                    <div className="col-head col-head--notes">Notas</div>
                    <div className="col-head col-head--status">Estado</div>
                    <div className="col-head col-head--action">Acción</div>
                  </>
                )}
              </div>

              <div className="orders-rows">
                {visibleOrders.length === 0 ? (
                  <div className="orders-empty">
                    No hay pedidos en esta categoría
                  </div>
                ) : (
                  visibleOrders.map((order) => (
                    <OrderRow
                      key={order.id}
                      order={order}
                      isSelected={order.id === selectedId}
                      advancing={advancing}
                      contracted={contracted}
                      onSelect={() =>
                        setSelectedId(order.id === selectedId ? null : order.id)
                      }
                      onAdvance={(e) => handleAdvance(e, order)}
                      onDismiss={(e) => {
                        e.stopPropagation();
                        dismissOrder(order.id);
                      }}
                    />
                  ))
                )}
              </div>
            </div>

            {panelOpen && selectedOrder && (
              <OrderDetail
                order={panelOrder}
                onClose={() => setSelectedId(null)}
                onAdvance={handleAdvance}
                advancing={advancing}
                token={token}
                branchId={branchId}
                onRefetch={refetchDetail}
                onRefresh={refresh}
                onPaymentConfirmed={updatePaymentInList}
              />
            )}
          </>
        )}
      </div>

      {/* ── Shift banner ─────────────────────────────────────── */}
      {shift && (
        <div className="orders-shift-banner">
          <span className="orders-shift-banner-dot" />
          Mostrando pedidos del turno actual desde {formatHour(shift.openedAt)}
        </div>
      )}
    </div>
  );
}

// ── OrderRow ──────────────────────────────────────────────────

function OrderRow({
  order,
  isSelected,
  advancing,
  contracted,
  onSelect,
  onAdvance,
  onDismiss,
}) {
  const cfg = STATUS_CONFIG[order.status] ?? {};
  const next = getNextStatus(order.status, order.orderType);
  const isTerminal = TERMINAL.has(order.status);

  return (
    <div
      className={[
        "orders-row",
        isSelected ? "orders-row--selected" : "",
        isTerminal ? "orders-row--cancelled" : "",
        contracted ? "orders-row--contracted" : "",
        order.status === "CANCELLATION_REQUESTED"
          ? "orders-row--cancel-request"
          : "",
      ]
        .filter(Boolean)
        .join(" ")}
      onClick={onSelect}
    >
      {/* PEDIDO */}
      <div className="col-order">
        <span className="order-id">{shortId(order.id)}</span>
        {!contracted && (
          <span className="order-time">{formatTime(order.createdAt)}</span>
        )}
      </div>

      {/* CLIENTE */}
      <div className="col-customer">
        <div
          className="customer-avatar"
          style={
            contracted
              ? { width: 28, height: 28, minWidth: 28, fontSize: 10 }
              : undefined
          }
          aria-hidden="true"
        >
          {getInitials(order.customerName)}
        </div>
        <div className="customer-info">
          <span className="customer-name">{order.customerName ?? "—"}</span>
          {!contracted && order.customerPhone && (
            <span className="customer-phone">
              <PhoneIcon />
              {order.customerPhone}
            </span>
          )}
          {!contracted &&
            order.orderType === "DELIVERY" &&
            order.deliveryAddress && (
              <span className="customer-address">
                <PinIcon />
                {order.deliveryAddress}
              </span>
            )}
        </div>
      </div>

      {/* PRODUCTOS — hidden when contracted */}
      {!contracted && (
        <div className="col-items">
          {order.items?.slice(0, 2).map((item, i) => (
            <span key={i} className="item-line">
              <span className="item-qty">{item.quantity}×</span>{" "}
              {item.productName}
            </span>
          ))}
          {order.items?.length > 2 && (
            <span className="item-more">+{order.items.length - 2} más</span>
          )}
        </div>
      )}

      {/* PAGO */}
      <div className="col-pago">
        {order.paymentStatus && (
          <span
            className="pago-status"
            style={{ color: PAYMENT_STATUS_COLOR[order.paymentStatus] }}
          >
            <PaymentStatusIcon status={order.paymentStatus} />
            {PAYMENT_STATUS_LABEL[order.paymentStatus] ?? order.paymentStatus}
          </span>
        )}
      </div>

      {/* NOTAS */}
      <div className={`col-notes${contracted ? " col-notes--contracted" : ""}`}>
        {order.notes ? (
          <span className="notes-text">"{order.notes}"</span>
        ) : (
          <span className="notes-empty">—</span>
        )}
      </div>

      {/* ESTADO */}
      <div className="col-status">
        <span
          className="status-badge"
          style={{
            backgroundColor: cfg.bg,
            color: cfg.color,
            borderColor: cfg.border,
          }}
        >
          <span className="status-dot" style={{ backgroundColor: cfg.color }} />
          {cfg.label ?? order.status}
        </span>
        {!contracted && (
          <span className="order-type-label">
            {order.orderType === "DELIVERY" ? (
              <>
                <ScooterIcon /> Delivery
              </>
            ) : (
              <>
                <StoreIcon /> Retiro en local
              </>
            )}
          </span>
        )}
      </div>

      {/* ACCIÓN — hidden when contracted */}
      {!contracted && (
        <div className="col-action">
          {isTerminal ? (
            <button
              className="action-dismiss-btn"
              type="button"
              onClick={onDismiss}
              aria-label="Descartar pedido"
            >
              <TrashIcon />
            </button>
          ) : next ? (
            <button
              className="action-advance-btn"
              type="button"
              onClick={onAdvance}
            >
              {advancing === order.id ? "···" : "Avanzar →"}
            </button>
          ) : order.status === "CANCELLATION_REQUESTED" ? (
            <span className="action-cancel-request-badge">⚠ Resolver</span>
          ) : (
            <span className="action-none">—</span>
          )}
        </div>
      )}
    </div>
  );
}

// ── OrderDetail ───────────────────────────────────────────────

function OrderDetail({
  order,
  onClose,
  onAdvance,
  advancing,
  token,
  branchId,
  onRefetch,
  onRefresh,
  onPaymentConfirmed,
}) {
  const cfg = STATUS_CONFIG[order.status] ?? {};
  const payCfg = PAYMENT_BADGE_CONFIG[order.paymentStatus] ?? {};
  const next = getNextStatus(order.status, order.orderType);
  const isTerminal = TERMINAL.has(order.status);

  const [paying, setPaying] = useState(false);
  const [actionLoading, setActionLoading] = useState(null);
  const [cancelRequestLoading, setCancelRequestLoading] = useState(null);
  const [cancelConfirming, setCancelConfirming] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

  const canGoBack = goBackAllowed(order.status);
  const canCancel = cancelAllowed(order.status);

  const confirmPayment = async (e) => {
    e.stopPropagation();
    setPaying(true);
    try {
      const payHeaders = { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
      if (branchId != null) payHeaders["X-Branch-Id"] = String(branchId)
      await apiFetch(`${API_URL}/backoffice/orders/${order.id}/payment`, {
        method: "PATCH",
        headers: payHeaders,
        body: JSON.stringify({ action: "CONFIRM" }),
      });
      onPaymentConfirmed(order.id, "APPROVED");
      onRefetch();
    } catch {
      /* empty */
    } finally {
      setPaying(false);
    }
  };

  const goBack = async (e) => {
    e.stopPropagation();
    setActionLoading("back");
    try {
      const backHeaders = { Authorization: `Bearer ${token}` }
      if (branchId != null) backHeaders["X-Branch-Id"] = String(branchId)
      await apiFetch(
        `${API_URL}/backoffice/orders/${order.id}/status/previous`,
        { method: "PATCH", headers: backHeaders },
      );
      onRefetch();
      onRefresh();
    } catch {
      /* empty */
    } finally {
      setActionLoading(null);
    }
  };

  const handleCancelConfirm = async () => {
    setActionLoading("cancel");
    try {
      const cancelHeaders = { "Content-Type": "application/json", Authorization: `Bearer ${token}` }
      if (branchId != null) cancelHeaders["X-Branch-Id"] = String(branchId)
      await apiFetch(`${API_URL}/backoffice/orders/${order.id}/status`, {
        method: "PATCH",
        headers: cancelHeaders,
        body: JSON.stringify({ nextStatus: "CANCELLED", reason: cancelReason.trim() }),
      });
      onRefetch();
      onRefresh();
    } catch {
      /* empty */
    } finally {
      setActionLoading(null);
      setCancelConfirming(false);
      setCancelReason("");
    }
  };

  const handleCancelRequest = async (action) => {
    const key = action === "APPROVE" ? "approve" : "reject";
    setCancelRequestLoading(key);
    try {
      await resolveCancelRequest(order.id, action, token, branchId);
      onRefetch();
      onRefresh();
    } catch {
      /* silent */
    } finally {
      setCancelRequestLoading(null);
    }
  };

  const sequence =
    order.orderType === "DELIVERY" ? SEQUENCE_DELIVERY : SEQUENCE_TAKEAWAY;
  const currentIdx = sequence.indexOf(order.status);
  const historyMap = {};
  if (order.statusHistory) {
    order.statusHistory.forEach((h) => {
      historyMap[h.toStatus] = h.changedAt;
    });
  }

  return (
    <div className="orders-detail-col">
      {/* ── Header ──────────────────────────────────────────── */}
      <div className="orders-detail-header">
        <span className="detail-order-id">{shortId(order.id)}</span>
        <span className="detail-order-time">{formatTime(order.createdAt)}</span>
        <div style={{ flex: 1 }} />
        <button
          className="detail-close-btn"
          type="button"
          onClick={onClose}
          aria-label="Cerrar"
        >
          <CloseIcon />
        </button>
      </div>

      {/* ── Body ─────────────────────────────────────────────── */}
      <div className="orders-detail-body">
        <div className="detail-four-grid">
          {/* Cliente — top left */}
          <div className="detail-block">
            <div className="detail-overline">CLIENTE</div>
            <div className="detail-customer-row">
              <div className="detail-customer-avatar">
                {getInitials(order.customerName)}
              </div>
              <div className="detail-customer-info">
                <div className="detail-customer-name">
                  {order.customerName ?? "—"}
                </div>
                {order.customerPhone && (
                  <div className="detail-customer-sub">
                    <PhoneIcon size={12} />
                    {order.customerPhone}
                  </div>
                )}
                {order.orderType === "DELIVERY" && order.deliveryAddress && (
                  <div className="detail-customer-sub">
                    <PinIcon size={12} />
                    {order.deliveryAddress}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Estado — top right */}
          <div className="detail-block">
            <div className="detail-overline">ESTADO DEL PEDIDO</div>
            <span
              className="status-badge"
              style={{
                backgroundColor: cfg.bg,
                color: cfg.color,
                borderColor: cfg.border,
              }}
            >
              <span
                className="status-dot"
                style={{ backgroundColor: cfg.color }}
              />
              {cfg.label ?? order.status}
            </span>
            <div className="detail-type-label">
              {order.orderType === "DELIVERY" ? (
                <>
                  <ScooterIcon size={11} /> Delivery
                </>
              ) : (
                <>
                  <StoreIcon size={11} /> Retiro en local
                </>
              )}
            </div>
          </div>

          {/* Origen — bottom left */}
          <div className="detail-block detail-block--origin">
            <div className="detail-overline">ORIGEN</div>
            <div className="detail-origin-name">
              {order.origin === "CLIENT" ? "App Roka" : "Local"}
            </div>
            <div className="detail-origin-time">
              {formatDateTime(order.createdAt)}
            </div>
          </div>

          {/* Pago — bottom right */}
          <div className="detail-block">
            <div className="detail-overline">PAGO</div>
            <span
              className="status-badge"
              style={{
                backgroundColor: payCfg.bg,
                color: payCfg.color,
                borderColor: payCfg.border,
              }}
            >
              <span
                className="status-dot"
                style={{ backgroundColor: payCfg.color }}
              />
              {PAYMENT_STATUS_LABEL[order.paymentStatus] ??
                order.paymentStatus ??
                "—"}
            </span>
            <div className="detail-payment-method">
              {PAYMENT_METHOD_LABEL[order.paymentMethod] ??
                order.paymentMethod ??
                "—"}
            </div>
          </div>
        </div>

        {/* Productos */}
        <div className="detail-block detail-block--full detail-products-block">
          <div className="detail-products-head">
            <span>CANT</span>
            <span>PRODUCTO</span>
            <span style={{ textAlign: "right" }}>P.UNIT</span>
            <span style={{ textAlign: "right" }}>SUBTOTAL</span>
          </div>
          {order.items?.map((item, i) => (
            <div
              key={i}
              className={`detail-product-row${i % 2 === 1 ? " detail-product-row--odd" : ""}`}
            >
              <span className="detail-prod-qty">x{item.quantity}</span>
              <span className="detail-prod-name">{item.productName}</span>
              <span className="detail-prod-price">
                {formatPrice(item.unitPrice)}
              </span>
              <span className="detail-prod-subtotal">
                {formatPrice(item.quantity * item.unitPrice)}
              </span>
            </div>
          ))}
          {order.subtotal != null && order.subtotal !== order.totalAmount && (
            <div className="detail-subtotal-row">
              <span>Subtotal</span>
              <span>{formatPrice(order.subtotal)}</span>
            </div>
          )}
          {order.deliveryFee > 0 && (
            <div className="detail-subtotal-row">
              <span>Envío</span>
              <span>{formatPrice(order.deliveryFee)}</span>
            </div>
          )}
          {order.serviceFee > 0 && (
            <div className="detail-subtotal-row">
              <span>Servicio</span>
              <span>{formatPrice(order.serviceFee)}</span>
            </div>
          )}
          <div className="detail-total-row">
            <span className="detail-total-label">TOTAL</span>
            <span className="detail-total-amount">
              {formatPrice(order.totalAmount)}
            </span>
          </div>
        </div>

        {/* Notas */}
        {order.notes && (
          <div className="detail-notes-block detail-block--full">
            <p className="detail-notes-text">"{order.notes}"</p>
          </div>
        )}

        {/* Motivo de cancelación */}
        {(order.status === "CANCELLED" || order.status === "CANCELLATION_REQUESTED") &&
          order.cancellationReason && (
            <div className="detail-cancel-reason-block">
              <div className="detail-overline">MOTIVO DE CANCELACIÓN</div>
              <p className="detail-cancel-reason-text">{order.cancellationReason}</p>
            </div>
          )}

        {/* Bottom row: Historial + Actions */}
        <div className="detail-bottom-row">
          {/* Left: Historial */}
          <div className="detail-bottom-left">
            <div className="detail-block">
              <div className="detail-overline">HISTORIAL</div>
              <div className="detail-timeline">
                {sequence.map((step, i) => {
                  const isLast = i === sequence.length - 1;
                  const isCurrent = i === currentIdx;
                  const isCompleted = i < currentIdx;
                  const isFuture = i > currentIdx;
                  const timestamp = historyMap[step];
                  return (
                    <div key={step} className="detail-timeline-row">
                      <div className="detail-timeline-step">
                        <div className="detail-timeline-left">
                          <div
                            className={
                              "detail-timeline-dot" +
                              (isCurrent
                                ? " detail-timeline-dot--current"
                                : isCompleted
                                  ? " detail-timeline-dot--completed"
                                  : " detail-timeline-dot--future")
                            }
                          />
                          {!isLast && <div className="detail-timeline-line" />}
                        </div>
                        <span
                          className={
                            "detail-timeline-label" +
                            (isCurrent
                              ? " detail-timeline-label--current"
                              : isFuture
                                ? " detail-timeline-label--future"
                                : "")
                          }
                        >
                          {STATUS_CONFIG[step]?.label ?? step}
                        </span>
                      </div>
                      {timestamp && (
                        <span className="detail-timeline-time">
                          {formatDateTime(timestamp)}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Right: Action buttons */}
          <div className="detail-bottom-right">
            {order.status === "CANCELLATION_REQUESTED" ? (
              <>
                <div className="detail-cancel-request-banner">
                  <span className="detail-cancel-request-title">
                    Solicitud de cancelación
                  </span>
                  <span className="detail-cancel-request-body">
                    El cliente solicitó cancelar este pedido.
                  </span>
                </div>
                <button
                  className="detail-action-btn detail-action-approve-cancel"
                  type="button"
                  onClick={() => handleCancelRequest("APPROVE")}
                  disabled={cancelRequestLoading !== null}
                >
                  {cancelRequestLoading === "approve"
                    ? "···"
                    : "Aprobar cancelación"}
                </button>
                <button
                  className="detail-action-btn detail-action-reject-cancel"
                  type="button"
                  onClick={() => handleCancelRequest("REJECT")}
                  disabled={cancelRequestLoading !== null}
                >
                  {cancelRequestLoading === "reject"
                    ? "···"
                    : "Rechazar cancelación"}
                </button>
              </>
            ) : (
              <>
                {next ? (
                  <button
                    className="detail-action-btn detail-action-advance"
                    type="button"
                    onClick={(e) => onAdvance(e, order)}
                    disabled={advancing === order.id}
                  >
                    {advancing === order.id
                      ? "···"
                      : `Avanzar a ${STATUS_CONFIG[next]?.label} →`}
                  </button>
                ) : isTerminal ? (
                  <div className="detail-action-btn detail-action-advance--done">
                    Pedido finalizado
                  </div>
                ) : (
                  <div className="detail-action-btn detail-action-advance--done">
                    Esperando pago
                  </div>
                )}

                {order.paymentMethod === "CASH" &&
                  order.paymentStatus !== "APPROVED" &&
                  !isTerminal && (
                    <button
                      className="detail-action-btn detail-action-pay"
                      type="button"
                      onClick={confirmPayment}
                      disabled={paying}
                    >
                      {paying ? "···" : "Marcar como pagado"}
                    </button>
                  )}

                {canGoBack && !cancelConfirming && (
                  <button
                    className="detail-action-btn detail-action-back"
                    type="button"
                    onClick={goBack}
                    disabled={actionLoading === "back"}
                  >
                    {actionLoading === "back" ? (
                      "···"
                    ) : (
                      <>
                        <ArrowLeftIcon /> Atrás
                      </>
                    )}
                  </button>
                )}

                {canCancel && (
                  cancelConfirming ? (
                    <div className="detail-cancel-reason-form">
                      <p className="detail-cancel-reason-label">Motivo de cancelación</p>
                      <textarea
                        className="detail-cancel-reason-input"
                        value={cancelReason}
                        onChange={(e) => setCancelReason(e.target.value)}
                        placeholder="Ingresá el motivo..."
                        rows={3}
                      />
                      <div className="detail-cancel-reason-actions">
                        <button
                          className="detail-action-btn detail-action-back"
                          type="button"
                          onClick={() => { setCancelConfirming(false); setCancelReason(""); }}
                          disabled={actionLoading === "cancel"}
                        >
                          <ArrowLeftIcon /> Volver
                        </button>
                        <button
                          className="detail-action-btn detail-action-cancel"
                          type="button"
                          onClick={handleCancelConfirm}
                          disabled={!cancelReason.trim() || actionLoading === "cancel"}
                        >
                          {actionLoading === "cancel" ? "···" : "Confirmar"}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      className="detail-action-btn detail-action-cancel"
                      type="button"
                      onClick={(e) => { e.stopPropagation(); setCancelConfirming(true); }}
                      disabled={actionLoading === "cancel"}
                    >
                      <XCancelIcon /> Cancelar
                    </button>
                  )
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
