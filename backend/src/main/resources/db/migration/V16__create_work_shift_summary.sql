CREATE TABLE work_shift_summary (
    id                UUID                     PRIMARY KEY,
    shift_id          UUID                     NOT NULL UNIQUE REFERENCES work_shift(id),
    total_orders      INTEGER                  NOT NULL,
    delivered_orders  INTEGER                  NOT NULL,
    cancelled_orders  INTEGER                  NOT NULL,
    total_revenue     NUMERIC(10,2)            NOT NULL,
    cash_revenue      NUMERIC(10,2)            NOT NULL,
    mp_revenue        NUMERIC(10,2)            NOT NULL,
    qr_revenue        NUMERIC(10,2)            NOT NULL,
    average_ticket    NUMERIC(10,2)            NOT NULL,
    calculated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
