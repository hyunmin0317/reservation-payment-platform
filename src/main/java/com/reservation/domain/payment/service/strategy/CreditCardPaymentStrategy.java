package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.client.PgClient;
import com.reservation.domain.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentStrategy extends ExternalPaymentStrategy {

    public CreditCardPaymentStrategy(PgClient pgClient) {
        super(pgClient);
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }
}