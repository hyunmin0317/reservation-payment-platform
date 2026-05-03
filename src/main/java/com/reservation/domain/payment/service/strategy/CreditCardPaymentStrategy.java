package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.client.PgClient;
import com.reservation.domain.payment.entity.PaymentMethod;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentStrategy extends ExternalPaymentStrategy {

    public CreditCardPaymentStrategy(PgClient pgClient, CircuitBreakerRegistry registry) {
        super(pgClient, registry, "creditCard");
    }

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CREDIT_CARD;
    }
}
