package com.reservation.domain.payment.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class YPayClient implements ExternalPaymentClient {

    @Override
    public PaymentResult pay(int amount) {
        return PaymentResult.success(UUID.randomUUID().toString());
    }

    @Override
    public void cancel(String transactionId) {
        // Y페이 결제 취소 처리
    }
}