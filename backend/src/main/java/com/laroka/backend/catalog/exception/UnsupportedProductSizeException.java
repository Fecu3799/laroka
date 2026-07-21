package com.laroka.backend.catalog.exception;

import com.laroka.backend.shared.exception.BusinessException;

/**
 * US-SIZE-04: se intentó cargar un tamaño que no se puede modelar como fila de
 * product_size. Hoy sólo CHICA es válido: GRANDE es implícito y su precio es siempre
 * product.price, así que una fila GRANDE crearía dos fuentes de verdad para el precio de la
 * pizza entera.
 *
 * La validación vive en el service y no sólo en la UI porque el enum ProductSizeName sigue
 * teniendo ambos valores: un POST directo a la API podría colar un GRANDE.
 *
 * Caso de negocio → 422 vía el handler de BusinessException.
 */
public class UnsupportedProductSizeException extends BusinessException {

	public UnsupportedProductSizeException(String message) {
		super(message);
	}
}
