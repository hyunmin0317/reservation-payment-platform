package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.pg.PgClient;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPaymentStrategy extends PgPaymentStrategy {

    public CreditCardPaymentStrategy(PgClient pgClient) {
        super(pgClient);
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }
}