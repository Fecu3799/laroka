CREATE UNIQUE INDEX work_shift_one_open_per_branch
ON work_shift (branch_id)
WHERE status = 'OPEN';
