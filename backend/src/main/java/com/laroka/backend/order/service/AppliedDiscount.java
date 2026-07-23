package com.laroka.backend.order.service;

import com.laroka.backend.order.entity.OrderDiscount;

/**
 * Descuento vigente de un pedido junto al nombre de quién lo aplicó (US-19-03).
 *
 * {@link OrderDiscount#getAppliedBy()} es un id plano —el módulo order no se acopla
 * a la entidad de staffuser—, así que el nombre se resuelve en el service y viaja
 * acá ya listo para el DTO. {@code appliedByName} puede ser null si el usuario fue
 * borrado: la fila del descuento sobrevive igual, es traza de auditoría.
 */
public record AppliedDiscount(OrderDiscount discount, String appliedByName) {}
