package com.laroka.backend.payment.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEventDTO {
    private String type;
    private String action;
    private Map<String, Object> data;
}
