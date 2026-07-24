package com.pedisur.backend.shift.service;

import com.pedisur.backend.shift.entity.WorkShift;

public record OpenShiftResult(WorkShift shift, boolean previousShiftClosed) {}
