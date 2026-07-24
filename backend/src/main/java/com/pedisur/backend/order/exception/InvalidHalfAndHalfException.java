package com.pedisur.backend.order.exception;

import com.pedisur.backend.shared.exception.BusinessException;

/**
 * US-HH-02: una combinación mitad y mitad no es válida. Caso de negocio (422, vía
 * GlobalExceptionHandler de BusinessException). El mensaje indica cuál validación falló:
 * la categoría no permite mitad y mitad, o los dos productos son de distinto tipo.
 */
public class InvalidHalfAndHalfException extends BusinessException {

	public InvalidHalfAndHalfException(String message) {
		super(message);
	}
}
