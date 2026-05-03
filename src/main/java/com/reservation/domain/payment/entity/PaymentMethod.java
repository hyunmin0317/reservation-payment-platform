package com.reservation.domain.payment.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {

    CREDIT_CARD(PaymentMethodType.EXTERNAL),
    Y_PAY(PaymentMethodType.EXTERNAL),
    Y_POINT(PaymentMethodType.INTERNAL);

    private final PaymentMethodType type;

    public enum PaymentMethodType {
        EXTERNAL,
        INTERNAL
    }
}
