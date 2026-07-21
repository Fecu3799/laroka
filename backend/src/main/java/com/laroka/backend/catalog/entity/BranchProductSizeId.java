package com.laroka.backend.catalog.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchProductSizeId implements Serializable {
	private Integer branch;
	private Integer productSize;
}
