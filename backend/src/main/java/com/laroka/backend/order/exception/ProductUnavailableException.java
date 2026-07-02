package com.laroka.backend.order.exception;

import com.laroka.backend.shared.exception.BusinessException;

/**
 * US-15-09: un pedido de origen CLIENT incluye un producto no disponible en la
 * sucursal (BranchProduct inexistente o available=false). Es un caso de negocio
 * (422), pero además del mensaje expone el productId como dato estructurado para
 * que el frontend (US-15-CF-05) lo remueva del carrito sin parsear el mensaje.
 */
public class ProductUnavailableException extends BusinessException {

    private final transient Integer productId;

    public ProductUnavailableException(Integer productId, String productName) {
        super("El producto no está disponible: " + productName);
        this.productId = productId;
    }

    public Integer getProductId() {
        return productId;
    }
}
