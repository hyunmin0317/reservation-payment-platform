package com.reservation.domain.order.dto;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.entity.OrderStatus;
import com.reservation.domain.payment.dto.PaymentResponse;
import com.reservation.domain.payment.entity.Payment;

import java.util.List;

public record BookingResponse(
        Long orderId,
        String orderNumber,
        int totalAmount,
        OrderStatus orderStatus,
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