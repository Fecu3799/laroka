package com.laroka.backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.laroka.backend.branch.repository.BranchRepository;

import jakarta.servlet.http.HttpServletRequest;

class SecurityUtilsTest {

    private SecurityUtils securityUtils;
    private BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        securityUtils = new SecurityUtils(branchRepository);
    }

    private CustomUserDetails staff(Integer branchId) {
        return new CustomUserDetails(1, branchId, 10, "staff@test.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
    }

    private CustomUserDetails manager(Integer branchId) {
        return new CustomUserDetails(3, branchId, 10, "manager@test.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
    }

    private CustomUserDetails admin() {
        return new CustomUserDetails(2, null, 10, "admin@test.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void staff_returnsBranchIdFromToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CustomUserDetails principal = staff(5);

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void staff_ignoresXBranchIdHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("99");
        CustomUserDetails principal = staff(5);

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(5);
    }

    @Test
    void manager_returnsBranchIdFromToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CustomUserDetails principal = manager(7);

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(7);
    }

    @Test
    void manager_ignoresXBranchIdHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("99");
        CustomUserDetails principal = manager(7);

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(7);
    }

    @Test
    void admin_withValidHeader_returnsParsedBranchId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("3");
        when(branchRepository.existsByIdAndTenantId(3, 10)).thenReturn(true);
        CustomUserDetails principal = admin();

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(3);
    }

    @Test
    void admin_withBranchFromOtherTenant_throws403() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("99");
        when(branchRepository.existsByIdAndTenantId(99, 10)).thenReturn(false);
        CustomUserDetails principal = admin();

        assertThatThrownBy(() -> securityUtils.resolveBranchId(principal, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void admin_withoutHeader_throws400() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn(null);
        CustomUserDetails principal = admin();

        assertThatThrownBy(() -> securityUtils.resolveBranchId(principal, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void admin_withBlankHeader_throws400() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("  ");
        CustomUserDetails principal = admin();

        assertThatThrownBy(() -> securityUtils.resolveBranchId(principal, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void admin_withInvalidHeader_throws400() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("not-a-number");
        CustomUserDetails principal = admin();

        assertThatThrownBy(() -> securityUtils.resolveBranchId(principal, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(HttpStatus.BAD_REQUEST.value()));
    }
}
