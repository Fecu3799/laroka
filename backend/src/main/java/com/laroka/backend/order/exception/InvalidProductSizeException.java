package com.laroka.backend.order.exception;

import com.laroka.backend.shared.exception.BusinessException;

/**
 * US-SIZE-03: el tamaño pedido para un ítem no es válido. Caso de negocio (422, vía
 * GlobalExceptionHandler de BusinessException). El mensaje indica cuál validación falló:
 * el tamaño no existe, no pertenece al producto del ítem, está inactivo, o se combinó
 * con mitad y mitad en el mismo ítem.
 */
public class InvalidProductSizeException extends BusinessException {

	public InvalidProductSizeException(String message) {
		super(message);
	}
}
