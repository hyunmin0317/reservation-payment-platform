package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.client.ExternalPaymentClient;
import com.reservation.domain.payment.client.PaymentResult;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

public abstract class ExternalPaymentStrategy implements PaymentStrategy {

    private final ExternalPaymentClient client;
    private final CircuitBreaker circuitBreaker;

    protected ExternalPaymentStrategy(ExternalPaymentClient client, CircuitBreakerRegistry registry, String circuitBreakerName) {
        this.client = client;
        this.circuitBreaker = registry.circuitBreaker(circuitBreakerName);
    }

    @Override
    public void pay(Payment payment, Order order) {
        PaymentResult result;
        try {
            result = circuitBreaker.executeSupplier(() -> client.pay(payment.getAmount()));
        } catch (CallNotPermittedException e) {
            payment.fail();
            throw new GeneralException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            payment.fail();
            throw new GeneralException(ErrorCode.PAYMENT_TIMEOUT);
        }

        if (!result.success()) {
            payment.fail();
            throw new GeneralException(result.errorCode());
        }

        payment.approve(result.transactionId());
    }

    @Override
    public void cancel(Payment payment, Order order) {
        client.cancel(payment.getTransactionId());
    }
}
