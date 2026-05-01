package com.reservation.domain.payment.pg;

import com.reservation.domain.payment.entity.PaymentMethod;

public interface PgClient {

    PgPaymentResult pay(PaymentMethod method, int amount);

    void cancel(String transactionId);
}