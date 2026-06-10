CREATE TABLE work_shift (
    id         UUID                     PRIMARY KEY,
    branch_id  INTEGER                  NOT NULL REFERENCES branch(id),
    opened_by  INTEGER                  NOT NULL REFERENCES staff_user(id),
    closed_by  INTEGER                  REFERENCES staff_user(id),
    opened_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at  TIMESTAMP WITH TIME ZONE,
    status     VARCHAR(10)              NOT NULL
);

CREATE INDEX idx_work_shift_branch_status ON work_shift(branch_id, status);
