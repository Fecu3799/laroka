package com.laroka.backend.shift.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import com.laroka.backend.shared.security.SecurityUtils;
import com.laroka.backend.shift.dto.CurrentShiftResponseDTO;
import com.laroka.backend.shift.dto.ShiftHistoryItemDTO;
import com.laroka.backend.shift.entity.ShiftStatus;
import com.laroka.backend.shift.entity.WorkShift;
import com.laroka.backend.shift.entity.WorkShiftSummary;
import com.laroka.backend.shift.service.WorkShiftService;
import com.laroka.backend.staffuser.entity.StaffUser;

/**
 * Verifica la exposición del flag autoClose en el historial de turnos (US-16-04
 * plumbing): se deriva de closedBy == null en un turno ya CLOSED. Cierre manual
 * (closedBy seteado) ⇒ false; auto-cierre por duración máxima (closedBy null) ⇒
 * true. El flag aparece tanto en el ShiftHistoryItemDTO como en su summary.
 */
@ExtendWith(MockitoExtension.class)
class WorkShiftControllerTest {

    @Mock private WorkShiftService workShiftService;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private WorkShiftController controller;

    private WorkShift closedShift(StaffUser closedBy) {
        return WorkShift.builder()
            .id(UUID.randomUUID())
            .openedAt(OffsetDateTime.now().minusHours(5))
            .closedAt(OffsetDateTime.now())
            .openedBy(StaffUser.builder().name("Ana").build())
            .closedBy(closedBy)
            .status(ShiftStatus.CLOSED)
            .summary(WorkShiftSummary.builder().totalOrders(3).build())
            .build();
    }

    @Test
    void history_manuallyClosedShift_exposesAutoCloseFalse() {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        WorkShift manual = closedShift(StaffUser.builder().name("Beto").build());
        when(workShiftService.getShiftHistory(anyInt(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(manual)));

        ResponseEntity<Page<ShiftHistoryItemDTO>> res = controller.getShiftHistory(0, 20, null, null);

        ShiftHistoryItemDTO dto = res.getBody().getContent().get(0);
        assertThat(dto.isAutoClose()).isFalse();
        assertThat(dto.getSummary().isAutoClose()).isFalse();
    }

    @Test
    void history_autoClosedShift_exposesAutoCloseTrue() {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        WorkShift auto = closedShift(null); // sin closedBy ⇒ cerrado por el sistema
        when(workShiftService.getShiftHistory(anyInt(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(auto)));

        ResponseEntity<Page<ShiftHistoryItemDTO>> res = controller.getShiftHistory(0, 20, null, null);

        ShiftHistoryItemDTO dto = res.getBody().getContent().get(0);
        assertThat(dto.isAutoClose()).isTrue();
        assertThat(dto.getSummary().isAutoClose()).isTrue();
    }

    @Test
    void currentShift_openShift_exposesAutoCloseFalse() {
        when(securityUtils.resolveBranchId(any(), any())).thenReturn(1);
        // Turno abierto: closedBy null porque sigue en curso, no porque se auto-cerró.
        WorkShift open = WorkShift.builder()
            .id(UUID.randomUUID())
            .openedAt(OffsetDateTime.now().minusHours(1))
            .openedBy(StaffUser.builder().name("Ana").build())
            .status(ShiftStatus.OPEN)
            .build();
        when(workShiftService.getCurrentShift(anyInt())).thenReturn(Optional.of(open));

        ResponseEntity<CurrentShiftResponseDTO> res = controller.getCurrentShift(null, null);

        assertThat(res.getBody().isActive()).isTrue();
        assertThat(res.getBody().isAutoClose()).isFalse();
    }
}
