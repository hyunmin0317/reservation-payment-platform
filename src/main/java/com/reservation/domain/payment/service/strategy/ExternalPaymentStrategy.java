package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.client.ExternalPaymentClient;
import com.reservation.domain.payment.client.PaymentResult;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ExternalPaymentStrategy implements PaymentStrategy {

    private final ExternalPaymentClient client;

    @Override
    public void pay(Payment payment, Order order) {
        PaymentResult result;
        try {
            result = client.pay(payment.getAmount());
        } catch (Exception e) {
            payment.fail();
            throw new GeneralException(ErrorCode.PAYMENT_TIMEOUT);
        }

        if (!result.success()) {
            payment.fail();
            throw mapFailureToException(result.failureReason());
        }

        payment.approve(result.transactionId());
    }

    @Override
    public void cancel(Payment payment, Order order) {
        client.cancel(payment.getTransactionId());
    }

    private GeneralException mapFailureToException(String failureReason) {
        if (failureReason == null) {
            return new GeneralException(ErrorCode.PAYMENT_FAILED);
        }
        return switch (failureReason) {
            case "LIMIT_EXCEEDED" -> new GeneralException(ErrorCode.PAYMENT_LIMIT_EXCEEDED);
            case "TIMEOUT" -> new GeneralException(ErrorCode.PAYMENT_TIMEOUT);
            default -> new GeneralException(ErrorCode.PAYMENT_FAILED);
        };
    }
}
