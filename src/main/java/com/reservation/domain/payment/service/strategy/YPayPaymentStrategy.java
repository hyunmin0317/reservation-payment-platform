package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.pg.PgClient;
import org.springframework.stereotype.Component;

@Component
public class YPayPaymentStrategy extends PgPaymentStrategy {

    public YPayPaymentStrategy(PgClient pgClient) {
        super(pgClient);
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.Y_PAY;
    }
}