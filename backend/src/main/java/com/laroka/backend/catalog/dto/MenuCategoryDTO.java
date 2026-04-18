package com.laroka.backend.catalog.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuCategoryDTO {
	private Integer categoryId;
	private String categoryName;
	private List<MenuProductDTO> products;
}
