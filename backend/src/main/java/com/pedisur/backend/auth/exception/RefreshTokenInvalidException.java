package com.pedisur.backend.auth.exception;

public class RefreshTokenInvalidException extends RuntimeException {
	public RefreshTokenInvalidException() {
		super("Refresh token inválido, expirado o revocado");
	}
}
