package com.reservation.domain.payment.dto;

import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;

public record PaymentResponse(
        Long paymentId,
        PaymentMethod paymentMethod,
        int amount,
        PaymentStatus status,
        String transactionId
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getTransactionId()
        );
    }
}