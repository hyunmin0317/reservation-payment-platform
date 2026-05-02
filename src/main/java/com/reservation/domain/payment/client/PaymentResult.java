package com.reservation.domain.payment.client;

import com.reservation.global.exception.code.ErrorCode;

public record PaymentResult(
        boolean success,
        String transactionId,
        ErrorCode errorCode
) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }

    public static PaymentResult failure(ErrorCode errorCode) {
        return new PaymentResult(false, null, errorCode);
    }
}
