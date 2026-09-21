package com.subbtech.orders.order.api;

import com.subbtech.orders.order.app.IdempotencyKeyReuseException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 400s (validation, missing header) are rendered by Spring's problem-details support. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    ProblemDetail idempotencyKeyReuse(IdempotencyKeyReuseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
        problem.setTitle("Idempotency-Key reuse");
        return problem;
    }
}
