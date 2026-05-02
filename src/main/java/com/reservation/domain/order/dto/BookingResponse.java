package com.reservation.domain.order.dto;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.entity.OrderStatus;
import com.reservation.domain.payment.dto.PaymentResponse;
import com.reservation.domain.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "예약 결제 응답")
public record BookingResponse(
        @Schema(description = "주문 ID", example = "1")
        Long orderId,

        @Schema(description = "주문 번호", example = "ORD-20260502-ABC123")
        String orderNumber,

        @Schema(description = "총 결제 금액", example = "100000")
        int totalAmount,

        @Schema(description = "주문 상태", example = "COMPLETED")
        OrderStatus orderStatus,

        @Schema(description = "결제 내역 목록")
        List<PaymentResponse> payments
) {
    public static BookingResponse of(Order order, List<Payment> payments) {
        return new BookingResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getTotalAmount(),
                order.getStatus(),
                payments.stream().map(PaymentResponse::from).toList()
        );
    }
}