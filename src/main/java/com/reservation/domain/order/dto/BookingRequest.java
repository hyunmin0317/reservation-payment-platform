package com.reservation.domain.order.dto;

import com.reservation.domain.payment.dto.PaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BookingRequest(
        @NotNull Long productId,
        @NotEmpty @Valid List<PaymentRequest> payments
) {
}