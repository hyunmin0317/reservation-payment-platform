package com.reservation.domain.payment.pg;

public record PgPaymentResult(
        boolean success,
        String transactionId,
        String failureReason
) {
    public static PgPaymentResult success(String transactionId) {
        return new PgPaymentResult(true, transactionId, null);
    }

    public static PgPaymentResult failure(String reason) {
        return new PgPaymentResult(false, null, reason);
    }
}