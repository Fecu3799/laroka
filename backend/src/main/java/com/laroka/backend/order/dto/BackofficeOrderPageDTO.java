package com.laroka.backend.order.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackofficeOrderPageDTO {

    private List<BackofficeOrderResponseDTO> content;
    private long totalElements;
    private int totalPages;
}
