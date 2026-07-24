package com.pedisur.backend.shift.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pedisur.backend.notification.service.NotificationService;
import com.pedisur.backend.shift.entity.WorkShift;
import com.pedisur.backend.shift.service.WorkShiftService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShiftAutoCloseJob {

    private final WorkShiftService workShiftService;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 900_000)
    public void run() {
        List<WorkShift> expired = workShiftService.findExpiredOpenShifts();
        if (expired.isEmpty()) {
            return;
        }
        log.info("ShiftAutoCloseJob: {} turno(s) expirado(s) encontrado(s)", expired.size());
        int closed = 0;
        for (WorkShift shift : expired) {
            try {
                workShiftService.autoCloseShift(shift.getId());
                closed++;
                notificationService.sendShiftAutoClosedEvent(shift.getBranch().getId());
            } catch (Exception e) {
                log.error("ShiftAutoCloseJob: error al cerrar turno {} (sucursal {})",
                        shift.getId(), shift.getBranch().getId(), e);
            }
        }
        log.info("ShiftAutoCloseJob: {} turno(s) cerrado(s) automáticamente", closed);
    }
}
