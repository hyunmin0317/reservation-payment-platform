package com.reservation.domain.order.dto;

import com.reservation.domain.payment.dto.PaymentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "예약 결제 요청")
public record BookingRequest(
        @Schema(description = "상품 ID", example = "1")
        @NotNull Long productId,

        @Schema(description = "결제 수단 목록 (포인트 + 외부결제 조합 가능)")
        @NotEmpty @Valid List<PaymentRequest> payments
) {
}
