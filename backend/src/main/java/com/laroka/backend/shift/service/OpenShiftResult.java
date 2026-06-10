package com.laroka.backend.shift.service;

import com.laroka.backend.shift.entity.WorkShift;

public record OpenShiftResult(WorkShift shift, boolean previousShiftClosed) {}
