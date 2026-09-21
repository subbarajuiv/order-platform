package com.subbtech.orders.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerId,
        String currency,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<Item> items) {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }
}
