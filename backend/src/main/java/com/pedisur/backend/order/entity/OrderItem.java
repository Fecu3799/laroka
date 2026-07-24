package com.pedisur.backend.order.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.pedisur.backend.catalog.entity.Product;
import com.pedisur.backend.catalog.entity.ProductSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    private UUID id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // US-HH-01: segunda mitad de un ítem mitad y mitad. Null en ítems simples (comportamiento
    // actual sin cambios). La validación y el pricing de la combinación son US-HH-02 / US-HH-03.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_product_id")
    private Product secondProduct;

    // US-SIZE-03: tamaño elegido para este ítem. Null → ítem sin tamaño (precio base del
    // producto, comportamiento previo). Mutuamente excluyente con secondProduct: un ítem
    // con tamaño no puede ser mitad y mitad. unitPrice ya viene resuelto con el precio
    // efectivo del tamaño en la sucursal (US-SIZE-02).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_size_id")
    private ProductSize productSize;
}
