package com.laroka.backend.branch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "branch_qr")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchQR {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false, unique = true)
	private Branch branch;

	@Column(name = "mp_pos_id", nullable = false)
	private String mpPosId;

	@Column(name = "mp_qr_id", nullable = false)
	private String mpQrId;

	@Column(name = "active", nullable = false)
	private boolean active;
}
