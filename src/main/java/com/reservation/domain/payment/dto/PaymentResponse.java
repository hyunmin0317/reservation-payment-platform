package com.reservation.domain.payment.dto;

import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;

public record PaymentResponse(
        PaymentMethod method,
        int amount,
        PaymentStatus status
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getMethod(),
                payment.getAmount(),
                payment.getStatus()
        );
    }
}