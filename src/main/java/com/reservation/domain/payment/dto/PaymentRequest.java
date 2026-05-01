package com.reservation.domain.payment.dto;

import com.reservation.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(
        @NotNull PaymentMethod method,
        @Positive int amount
) {
}