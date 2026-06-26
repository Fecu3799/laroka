package com.laroka.backend.shift.job;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.notification.service.NotificationService;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.service.WorkShiftService;

@ExtendWith(MockitoExtension.class)
class ShiftAutoCloseJobTest {

    @Mock
    private WorkShiftService workShiftService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ShiftAutoCloseJob job;

    private WorkShift expiredShift(UUID id) {
        Branch branch = Branch.builder().id(1).name("Playa Unión").build();
        return WorkShift.builder()
            .id(id)
            .branch(branch)
            .openedAt(OffsetDateTime.now().minusHours(13))
            .status(ShiftStatus.OPEN)
            .build();
    }

    @Test
    void run_expiredShifts_closesEach() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(workShiftService.findExpiredOpenShifts()).thenReturn(List.of(expiredShift(id1), expiredShift(id2)));

        job.run();

        verify(workShiftService).autoCloseShift(id1);
        verify(workShiftService).autoCloseShift(id2);
    }

    @Test
    void run_noExpiredShifts_doesNotClose() {
        when(workShiftService.findExpiredOpenShifts()).thenReturn(List.of());

        job.run();

        verify(workShiftService, never()).autoCloseShift(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_errorInOneShift_continuesWithOthers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(workShiftService.findExpiredOpenShifts()).thenReturn(List.of(expiredShift(id1), expiredShift(id2)));
        doThrow(new RuntimeException("DB error")).when(workShiftService).autoCloseShift(id1);
        doNothing().when(workShiftService).autoCloseShift(id2);

        job.run();

        verify(workShiftService).autoCloseShift(id1);
        verify(workShiftService).autoCloseShift(id2);
    }

    @Test
    void run_expiredShift_notifiesBranchAfterClose() {
        UUID id = UUID.randomUUID();
        WorkShift shift = expiredShift(id);
        when(workShiftService.findExpiredOpenShifts()).thenReturn(List.of(shift));

        job.run();

        verify(notificationService).sendShiftAutoClosedEvent(shift.getBranch().getId());
    }
}
