package com.pedisur.backend.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pedisur.backend.catalog.dto.CategoryTypeResponseDTO;
import com.pedisur.backend.catalog.entity.CategoryType;

class CategoryTypeMapperTest {

	private final CategoryTypeMapper mapper = new CategoryTypeMapper();

	@Test
	void mapsBothCapabilityFlagsIndependently() {
		// US-SIZE-01: allowsSizes y allowsHalfAndHalf son independientes — el mapper no
		// puede derivar uno del otro ni colapsarlos en un solo concepto de "pizza".
		CategoryType soloTamanios = CategoryType.builder()
			.id(1).name("Milanesa").allowsHalfAndHalf(false).allowsSizes(true).active(true).build();
		CategoryType soloMitades = CategoryType.builder()
			.id(2).name("Empanada").allowsHalfAndHalf(true).allowsSizes(false).active(true).build();

		CategoryTypeResponseDTO dtoTamanios = mapper.toResponseDTO(soloTamanios);
		CategoryTypeResponseDTO dtoMitades = mapper.toResponseDTO(soloMitades);

		assertThat(dtoTamanios.isAllowsSizes()).isTrue();
		assertThat(dtoTamanios.isAllowsHalfAndHalf()).isFalse();
		assertThat(dtoMitades.isAllowsSizes()).isFalse();
		assertThat(dtoMitades.isAllowsHalfAndHalf()).isTrue();
	}

	@Test
	void mapsIdAndName() {
		CategoryType pizza = CategoryType.builder()
			.id(7).name("Pizza").allowsHalfAndHalf(true).allowsSizes(true).active(true).build();

		CategoryTypeResponseDTO dto = mapper.toResponseDTO(pizza);

		assertThat(dto.getId()).isEqualTo(7);
		assertThat(dto.getName()).isEqualTo("Pizza");
	}

	@Test
	void returnsNullForNullEntity() {
		assertThat(mapper.toResponseDTO(null)).isNull();
	}
}
