-- V41__add_discount_aggregates_to_shift_summary.sql - Descuentos del turno (US-20-02)
--
-- Agrega al resumen inmutable del turno los agregados de descuento de sus pedidos
-- DELIVERED: monto total descontado, cantidad de pedidos con descuento vigente, y el
-- desglose por motivo (mismas tres constantes de DiscountReason). Se calculan en
-- WorkShiftService.calculateSummary junto al resto de los totales y se persisten con
-- el summary (RN-TU-02), igual que cash/mp/qr_revenue.
--
-- "Descuento vigente" = la fila más reciente de order_discount por pedido si es
-- APPLIED; una reversión (REVERTED, US-19-06) no aporta descuento. Por eso los montos
-- cuadran con total_revenue, que ya factura el total efectivamente cobrado.
--
-- DEFAULT 0 para backfillear los summaries históricos sin descuentos, igual que V17.

ALTER TABLE work_shift_summary
    ADD COLUMN total_discount               NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN discounted_orders            INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN discount_customer_promo      NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_transfer_adjustment NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_other               NUMERIC(10,2) NOT NULL DEFAULT 0;
