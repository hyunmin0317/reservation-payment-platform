package com.reservation.domain.payment.client;

public interface ExternalPaymentClient {

    PaymentResult pay(int amount);

    void cancel(String transactionId);
}