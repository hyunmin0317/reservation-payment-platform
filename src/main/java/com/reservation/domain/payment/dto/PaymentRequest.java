package com.reservation.domain.payment.dto;

import com.reservation.domain.payment.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "결제 수단 요청")
public record PaymentRequest(
        @Schema(description = "결제 수단", example = "CREDIT_CARD")
        @NotNull PaymentMethod method,

        @Schema(description = "결제 금액", example = "50000")
        @Positive int amount
) {
}
