package com.laroka.backend.tenant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id", nullable = false, unique = true)
	private Tenant tenant;

	@Column(name = "business_name", nullable = false)
	private String businessName;

	@Column(name = "description", nullable = false)
	private String description;

	@Column(name = "instagram_url")
	private String instagramUrl;

	@Column(name = "facebook_url")
	private String facebookUrl;

	@Column(name = "whatsapp", length = 50)
	private String whatsapp;

	@Column(name = "logo_url", length = 500)
	private String logoUrl;

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
