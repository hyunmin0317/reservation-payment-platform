package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.pg.PgClient;
import com.reservation.domain.payment.pg.PgPaymentResult;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreditCardPaymentStrategy implements PaymentStrategy {

    private final PgClient pgClient;

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public void pay(Payment payment, Order order) {
        PgPaymentResult result = pgClient.pay(PaymentMethod.CREDIT_CARD, payment.getAmount());

        if (!result.success()) {
            payment.fail();
            throw new GeneralException(ErrorCode.PAYMENT_FAILED);
        }

        payment.approve(result.transactionId());
    }

    @Override
    public void cancel(Payment payment, Order order) {
        pgClient.cancel(payment.getTransactionId());
    }
}