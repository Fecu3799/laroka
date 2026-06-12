ALTER TABLE work_shift_summary ADD COLUMN delivery_orders   INT           NOT NULL DEFAULT 0;
ALTER TABLE work_shift_summary ADD COLUMN takeaway_orders   INT           NOT NULL DEFAULT 0;
ALTER TABLE work_shift_summary ADD COLUMN cancellation_rate NUMERIC(5,2)  NOT NULL DEFAULT 0;
