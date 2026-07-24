package com.pedisur.backend.order.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pedisur.backend.order.service.OrderExpirationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationJob {

    private final OrderExpirationService orderExpirationService;

    @Value("${order.expiration-minutes:30}")
    private long expirationMinutes;

    @Scheduled(fixedDelay = 900_000)
    public void run() {
        int count = orderExpirationService.cancelExpiredOrders(expirationMinutes);
        log.info("OrderExpirationJob: {} pedido(s) cancelado(s) por expiración", count);
    }
}
