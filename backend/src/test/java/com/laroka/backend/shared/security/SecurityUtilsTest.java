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

import jakarta.servlet.http.HttpServletRequest;

class SecurityUtilsTest {

    private SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = new SecurityUtils();
    }

    private CustomUserDetails staff(Integer branchId) {
        return new CustomUserDetails(1, branchId, "staff@test.com", null,
            List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
    }

    private CustomUserDetails admin() {
        return new CustomUserDetails(2, null, "admin@test.com", null,
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
    void admin_withValidHeader_returnsParsedBranchId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Branch-Id")).thenReturn("3");
        CustomUserDetails principal = admin();

        Integer result = securityUtils.resolveBranchId(principal, request);

        assertThat(result).isEqualTo(3);
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
