package com.reservation.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "체크아웃 가능 상태")
public enum CheckoutStatus {
    AVAILABLE,
    WAITING,
    SOLD_OUT
}
