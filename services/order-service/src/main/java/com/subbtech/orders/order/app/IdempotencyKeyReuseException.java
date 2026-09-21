package com.subbtech.orders.order.app;

/** The same Idempotency-Key was sent with a different request payload. */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("Idempotency-Key was already used with a different request payload");
    }
}
