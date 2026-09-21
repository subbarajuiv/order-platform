package com.subbtech.orders.order.api;

import com.subbtech.orders.order.app.CreateOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final CreateOrderService createOrder;

    public OrdersController(CreateOrderService createOrder) {
        this.createOrder = createOrder;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        CreateOrderService.Result result = createOrder.create(idempotencyKey, request);
        if (result.created()) {
            URI location = UriComponentsBuilder.fromPath("/orders/{id}")
                    .build(result.order().orderId());
            return ResponseEntity.created(location).body(result.order());
        }
        return ResponseEntity.ok(result.order());
    }
}
