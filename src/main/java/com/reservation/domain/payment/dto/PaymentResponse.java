package com.reservation.domain.payment.dto;

import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 결과 응답")
public record PaymentResponse(
        @Schema(description = "결제 수단", example = "CREDIT_CARD")
        PaymentMethod method,

        @Schema(description = "결제 금액", example = "50000")
        int amount,

        @Schema(description = "결제 상태", example = "SUCCESS")
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