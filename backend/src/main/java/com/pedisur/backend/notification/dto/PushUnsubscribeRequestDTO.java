package com.pedisur.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushUnsubscribeRequestDTO {

    @NotBlank
    private String endpoint;
}
