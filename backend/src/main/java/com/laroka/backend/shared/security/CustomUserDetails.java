package com.laroka.backend.shared.security;

import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public class CustomUserDetails implements UserDetails {
	private static final long serialVersionUID = 1L;

	private final Integer userId;
	private final Integer branchId;
	private final String username;
	private final String password;
	private final Collection<? extends GrantedAuthority> authorities;

	public CustomUserDetails(Integer userId, Integer branchId, String username, String password,
			Collection<? extends GrantedAuthority> authorities) {
		this.userId = userId;
		this.branchId = branchId;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
	}

	public Integer getUserId() {
		return userId;
	}

	public Integer getBranchId() {
		return branchId;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
