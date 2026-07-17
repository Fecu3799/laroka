package com.laroka.backend.notification.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.laroka.backend.branch.entity.Branch;
import com.laroka.backend.branch.repository.BranchRepository;
import com.laroka.backend.notification.email.EmailDeliveryException;
import com.laroka.backend.notification.email.EmailService;
import com.laroka.backend.shared.exception.EntityNotFoundException;
import com.laroka.backend.staffuser.entity.StaffUser;
import com.laroka.backend.staffuser.repository.StaffUserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Arma y envía por email los reportes de bugs del backoffice (US-17-07). No
 * persiste nada en DB: es un email, no un ticket con estado.
 */
@Slf4j
@Service
public class BugReportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final EmailService emailService;
    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;
    private final String bugReportEmail;

    public BugReportService(
            EmailService emailService,
            StaffUserRepository staffUserRepository,
            BranchRepository branchRepository,
            @Value("${bug-report.email:}") String bugReportEmail) {
        this.emailService = emailService;
        this.staffUserRepository = staffUserRepository;
        this.branchRepository = branchRepository;
        this.bugReportEmail = bugReportEmail;
    }

    /**
     * Arma el email con los datos del reporte + la identidad del operador (nombre,
     * rol, sucursal activa) y lo envía a la casilla fija configurada. Si el
     * proveedor falla, lanza {@link EmailDeliveryException} (el handler la mapea a 502).
     *
     * @param userId         id del operador que reporta (del JWT)
     * @param activeBranchId sucursal activa ya resuelta (token o X-Branch-Id)
     * @param description    descripción del problema (tipeada por el operador)
     * @param url            URL donde ocurrió (capturada por el frontend)
     * @param userAgent      user agent del navegador (capturado por el frontend)
     * @param screenshotUrl  URL pública de la captura adjunta (opcional, puede ser null)
     */
    public void report(Integer userId, Integer activeBranchId, String description, String url,
                       String userAgent, String screenshotUrl) {
        StaffUser reporter = staffUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + userId));
        Branch branch = branchRepository.findById(activeBranchId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada: " + activeBranchId));

        String role = reporter.getRole().name();
        String subject = "[LaRoka] Reporte de bug — " + reporter.getName() + " (" + role + ")";
        String body = buildBody(reporter.getName(), role, branch.getName(), description, url, userAgent, screenshotUrl);

        boolean sent = emailService.send(bugReportEmail, subject, body);
        if (!sent) {
            log.warn("Bug report NO enviado — el proveedor de email falló | reporter={} branch={}",
                    reporter.getName(), branch.getName());
            throw new EmailDeliveryException("El proveedor de email rechazó el reporte de bug");
        }
        log.info("Bug report enviado | reporter={} role={} branch={}", reporter.getName(), role, branch.getName());
    }

    private String buildBody(String reporterName, String role, String branchName,
                             String description, String url, String userAgent, String screenshotUrl) {
        // Si hay captura, se agrega su URL en una línea propia. En texto plano los
        // clientes de mail (Gmail, etc.) la vuelven un link clickeable automáticamente.
        String screenshotLine = screenshotUrl != null && !screenshotUrl.isBlank()
                ? "\nCaptura de pantalla: " + screenshotUrl + "\n"
                : "";

        return """
                Nuevo reporte de bug — LaRoka Backoffice

                Reportado por: %s (%s)
                Sucursal: %s
                Fecha/hora: %s

                Descripción:
                %s

                URL: %s
                User agent: %s
                %s""".formatted(
                reporterName,
                role,
                branchName,
                LocalDateTime.now().format(TIMESTAMP_FORMAT),
                description,
                url != null && !url.isBlank() ? url : "—",
                userAgent != null && !userAgent.isBlank() ? userAgent : "—",
                screenshotLine);
    }
}
