package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.client.YPayClient;
import com.reservation.domain.payment.entity.PaymentMethod;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

@Component
public class YPayPaymentStrategy extends ExternalPaymentStrategy {

    public YPayPaymentStrategy(YPayClient yPayClient, CircuitBreakerRegistry registry) {
        super(yPayClient, registry);
    }

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.Y_PAY;
    }
}
