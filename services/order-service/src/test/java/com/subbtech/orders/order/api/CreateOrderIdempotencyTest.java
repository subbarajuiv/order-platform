package com.subbtech.orders.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.subbtech.orders.order.TestcontainersConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CreateOrderIdempotencyTest {

    private static final String VALID_BODY = """
            {
              "customerId": "c-1",
              "currency": "EUR",
              "items": [
                { "sku": "A", "quantity": 2, "unitPrice": "9.99" },
                { "sku": "B", "quantity": 1, "unitPrice": "5.00" }
              ]
            }
            """;

    private static final String OTHER_BODY = """
            {
              "customerId": "c-1",
              "currency": "EUR",
              "items": [
                { "sku": "A", "quantity": 3, "unitPrice": "9.99" }
              ]
            }
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void sameKeyAndSamePayload_returnsSameOrder_andCreatesOneRow() throws Exception {
        String key = newKey();

        MockHttpServletResponse first = mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String orderId = JsonPath.read(first.getContentAsString(), "$.orderId");

        MockHttpServletResponse second = mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(first.getHeader("Location")).endsWith("/orders/" + orderId);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(orderRows(key)).isEqualTo(1);
        assertThat(itemRows(orderId)).isEqualTo(2);
    }

    // Fail fast if a regression makes requests block instead of returning.
    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void concurrentRequestsWithSameKey_createExactlyOneOrder() throws Exception {
        String key = newKey();
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<MockHttpServletResponse> request = () -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(post("/orders")
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_BODY))
                            .andReturn().getResponse();
                };
                futures.add(pool.submit(request));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(responses).filteredOn(r -> r.getStatus() == 201).hasSize(1);
            assertThat(responses).filteredOn(r -> r.getStatus() == 200).hasSize(threads - 1);
            assertThat(responses).extracting(r -> r.getContentAsString()).containsOnly(
                    responses.get(0).getContentAsString());
            assertThat(orderRows(key)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameKeyDifferentPayload_returns422_andDoesNotChangeTheOrder() throws Exception {
        String key = newKey();
        MockHttpServletResponse first = mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OTHER_BODY))
                .andExpect(status().isUnprocessableEntity());

        MockHttpServletResponse replay = mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(orderRows(key)).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/orders")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // no items
            "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"items\":[]}",
            // items missing
            "{\"customerId\":\"c-1\",\"currency\":\"EUR\"}",
            // quantity must be positive
            "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"items\":[{\"sku\":\"A\",\"quantity\":0,\"unitPrice\":\"1.00\"}]}",
            // negative price
            "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"items\":[{\"sku\":\"A\",\"quantity\":1,\"unitPrice\":\"-1.00\"}]}",
            // currency must be 3 upper-case letters
            "{\"customerId\":\"c-1\",\"currency\":\"euro\",\"items\":[{\"sku\":\"A\",\"quantity\":1,\"unitPrice\":\"1.00\"}]}",
            // blank customer
            "{\"customerId\":\" \",\"currency\":\"EUR\",\"items\":[{\"sku\":\"A\",\"quantity\":1,\"unitPrice\":\"1.00\"}]}",
            // blank sku
            "{\"customerId\":\"c-1\",\"currency\":\"EUR\",\"items\":[{\"sku\":\"\",\"quantity\":1,\"unitPrice\":\"1.00\"}]}"
    })
    void invalidBody_returns400_andStoresNothing(String body) throws Exception {
        String key = newKey();

        mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(orderRows(key)).isZero();
    }

    @Test
    void differentKeys_createDifferentOrders() throws Exception {
        String first = createOrder(newKey());
        String second = createOrder(newKey());

        assertThat(first).isNotEqualTo(second);
    }

    private String createOrder(String key) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.orderId");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private int orderRows(String idempotencyKey) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(Integer.class)
                .single();
    }

    private int itemRows(String orderId) {
        return jdbc.sql("SELECT count(*) FROM order_items WHERE order_id = :id")
                .param("id", UUID.fromString(orderId))
                .query(Integer.class)
                .single();
    }
}
