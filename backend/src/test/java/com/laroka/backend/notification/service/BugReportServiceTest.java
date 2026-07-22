package com.laroka.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.notification.email.EmailDeliveryException;
import com.laroka.backend.notification.email.EmailService;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.entity.UserRole;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class BugReportServiceTest {

    private static final String BUG_REPORT_EMAIL = "dueno@pedisur.app";
    private static final Integer USER_ID = 7;
    private static final Integer BRANCH_ID = 3;

    @Mock private EmailService emailService;
    @Mock private StaffUserRepository staffUserRepository;
    @Mock private BranchRepository branchRepository;

    private BugReportService service;

    @BeforeEach
    void setUp() {
        service = new BugReportService(emailService, staffUserRepository, branchRepository, BUG_REPORT_EMAIL);

        StaffUser reporter = StaffUser.builder()
                .id(USER_ID)
                .name("Ana Operadora")
                .role(UserRole.STAFF)
                .build();
        Branch branch = Branch.builder()
                .id(BRANCH_ID)
                .name("Puerto Madryn")
                .build();

        when(staffUserRepository.findById(USER_ID)).thenReturn(Optional.of(reporter));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
    }

    // --- armado correcto del email con todos los campos ---

    @Test
    void report_success_buildsEmailWithAllFields() {
        when(emailService.send(eq(BUG_REPORT_EMAIL), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        service.report(USER_ID, BRANCH_ID, "El botón de pago no responde",
                "https://backoffice.pedisur.app/orders", "Mozilla/5.0 (Macintosh)", null);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(BUG_REPORT_EMAIL), subject.capture(), body.capture());

        // Asunto: nombre + rol del reportante
        assertThat(subject.getValue()).contains("Ana Operadora").contains("STAFF");
        // Cuerpo: todos los campos requeridos por la US
        assertThat(body.getValue())
                .contains("Ana Operadora")
                .contains("STAFF")
                .contains("Puerto Madryn")
                .contains("El botón de pago no responde")
                .contains("https://backoffice.pedisur.app/orders")
                .contains("Mozilla/5.0 (Macintosh)");
        // Sin captura: no aparece la línea de screenshot.
        assertThat(body.getValue()).doesNotContain("Captura de pantalla");
    }

    // --- captura adjunta: la URL viaja en el cuerpo del email ---

    @Test
    void report_withScreenshot_includesUrlInBody() {
        when(emailService.send(eq(BUG_REPORT_EMAIL), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        String screenshotUrl = "https://pub-xxxx.r2.dev/bug-reports/abc123.png";
        service.report(USER_ID, BRANCH_ID, "Se rompió el checkout",
                "https://x", "UA", screenshotUrl);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq(BUG_REPORT_EMAIL), org.mockito.ArgumentMatchers.anyString(), body.capture());
        assertThat(body.getValue())
                .contains("Captura de pantalla")
                .contains(screenshotUrl);
    }

    // --- fallo del proveedor → EmailDeliveryException (el handler la mapea a 502) ---

    @Test
    void report_providerFails_throwsEmailDeliveryException() {
        when(emailService.send(eq(BUG_REPORT_EMAIL), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.report(USER_ID, BRANCH_ID, "Algo falló", "https://x", "UA", null))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
