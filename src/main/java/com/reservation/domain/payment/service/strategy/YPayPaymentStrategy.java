package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.payment.client.YPayClient;
import com.reservation.domain.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class YPayPaymentStrategy extends ExternalPaymentStrategy {

    public YPayPaymentStrategy(YPayClient yPayClient) {
        super(yPayClient);
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.Y_PAY;
    }
}