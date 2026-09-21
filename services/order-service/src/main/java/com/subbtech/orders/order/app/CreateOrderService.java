package com.subbtech.orders.order.app;

import com.subbtech.orders.order.api.CreateOrderRequest;
import com.subbtech.orders.order.api.OrderResponse;
import com.subbtech.orders.order.persistence.OrderRepository;
import com.subbtech.orders.order.persistence.OrderRepository.NewOrder;
import com.subbtech.orders.order.persistence.OrderRepository.StoredOrder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateOrderService {

    private static final String STATUS_CREATED = "CREATED";

    public record Result(OrderResponse order, boolean created) {
    }

    private final OrderRepository orders;
    private final Clock clock;

    public CreateOrderService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional
    public Result create(String idempotencyKey, CreateOrderRequest request) {
        List<OrderRepository.Item> items = request.items().stream()
                .map(i -> new OrderRepository.Item(i.sku(), i.quantity(), i.unitPrice()))
                .toList();
        BigDecimal total = items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Postgres stores microseconds; truncate so what we return equals what we persist.
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        UUID orderId = UUID.randomUUID();
        String requestHash = requestHash(request);
        boolean inserted = orders.insertIfAbsent(new NewOrder(
                orderId, idempotencyKey, requestHash, request.customerId(),
                request.currency(), STATUS_CREATED, total, createdAt));
        if (inserted) {
            orders.insertItems(orderId, items);
        }

        // Always answer from the persisted row so a replay is identical to the first response.
        StoredOrder stored = orders.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Order for idempotency key vanished inside its own transaction"));
        if (!inserted && !stored.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }
        return new Result(toResponse(stored), inserted);
    }

    private static OrderResponse toResponse(StoredOrder order) {
        return new OrderResponse(
                order.id(), order.customerId(), order.currency(), order.status(),
                order.totalAmount(), order.createdAt(),
                order.items().stream()
                        .map(i -> new OrderResponse.Item(i.sku(), i.quantity(), i.unitPrice()))
                        .toList());
    }

    /** SHA-256 over the normalized payload, so formatting differences (9.9 vs 9.90) hash the same. */
    private static String requestHash(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder()
                .append(request.customerId()).append('|')
                .append(request.currency());
        for (CreateOrderRequest.Item item : request.items()) {
            canonical.append('|').append(item.sku())
                    .append(':').append(item.quantity())
                    .append(':').append(item.unitPrice().stripTrailingZeros().toPlainString());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", e);
        }
    }
}
