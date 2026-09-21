package com.subbtech.orders.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String customerId,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter upper-case ISO currency code")
        String currency,
        @NotEmpty List<@NotNull @Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 100) String sku,
            @Positive int quantity,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {
    }
}
