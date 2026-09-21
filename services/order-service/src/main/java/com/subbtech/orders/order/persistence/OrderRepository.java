package com.subbtech.orders.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
    }

    public record NewOrder(
            UUID id,
            String idempotencyKey,
            String requestHash,
            String customerId,
            String currency,
            String status,
            BigDecimal totalAmount,
            Instant createdAt) {
    }

    public record StoredOrder(
            UUID id,
            String requestHash,
            String customerId,
            String currency,
            String status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<Item> items) {
    }

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically claims the idempotency key. Returns true if this call inserted the order, false if
     * an order with the same key already exists (a concurrent duplicate waits on the unique index
     * until the first transaction commits, then gets false).
     */
    public boolean insertIfAbsent(NewOrder order) {
        int rows = jdbc.sql("""
                        INSERT INTO orders (id, idempotency_key, request_hash, customer_id, currency,
                                            status, total_amount, created_at)
                        VALUES (:id, :key, :hash, :customerId, :currency, :status, :total, :createdAt)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("id", order.id())
                .param("key", order.idempotencyKey())
                .param("hash", order.requestHash())
                .param("customerId", order.customerId())
                .param("currency", order.currency())
                .param("status", order.status())
                .param("total", order.totalAmount())
                .param("createdAt", OffsetDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC))
                .update();
        return rows == 1;
    }

    public void insertItems(UUID orderId, List<Item> items) {
        int lineNo = 1;
        for (Item item : items) {
            jdbc.sql("""
                            INSERT INTO order_items (order_id, line_no, sku, quantity, unit_price)
                            VALUES (:orderId, :lineNo, :sku, :quantity, :unitPrice)
                            """)
                    .param("orderId", orderId)
                    .param("lineNo", lineNo++)
                    .param("sku", item.sku())
                    .param("quantity", item.quantity())
                    .param("unitPrice", item.unitPrice())
                    .update();
        }
    }

    public Optional<StoredOrder> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.sql("""
                        SELECT id, request_hash, customer_id, currency, status, total_amount, created_at
                        FROM orders WHERE idempotency_key = :key
                        """)
                .param("key", idempotencyKey)
                .query((rs, rowNum) -> new StoredOrder(
                        rs.getObject("id", UUID.class),
                        rs.getString("request_hash"),
                        rs.getString("customer_id"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        List.of()))
                .optional()
                .map(order -> new StoredOrder(
                        order.id(), order.requestHash(), order.customerId(), order.currency(),
                        order.status(), order.totalAmount(), order.createdAt(), findItems(order.id())));
    }

    private List<Item> findItems(UUID orderId) {
        return jdbc.sql("""
                        SELECT sku, quantity, unit_price FROM order_items
                        WHERE order_id = :orderId ORDER BY line_no
                        """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new Item(
                        rs.getString("sku"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")))
                .list();
    }
}
