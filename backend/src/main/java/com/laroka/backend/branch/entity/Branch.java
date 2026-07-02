package com.laroka.backend.branch.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.laroka.backend.tenant.entity.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branch")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "address", nullable = false)
	private String address;

	@Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
	private BigDecimal deliveryFee;

	@Column(name = "service_fee", nullable = false, precision = 10, scale = 2)
	private BigDecimal serviceFee;

	@Column(name = "estimated_delivery_minutes", nullable = false)
	private Integer estimatedDeliveryMinutes;

	@Column(name = "phone", nullable = false)
	private String phone;

	@Column(name = "image_url")
	private String imageUrl;

	@Column(name = "accepting_orders", nullable = false)
	private boolean acceptingOrders;

	@Builder.Default
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Builder.Default
	@Column(name = "max_shift_duration_minutes", nullable = false)
	private Integer maxShiftDurationMinutes = 720;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
