package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.pg.PgClient;
import com.reservation.domain.payment.pg.PgPaymentResult;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PgClient pgClient;

    @Transactional
    public Payment pay(Order order, PaymentMethod method, int amount) {
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(method)
                .amount(amount)
                .build();

        if (method == PaymentMethod.Y_POINT) {
            User user = userRepository.findById(order.getUser().getId())
                    .orElseThrow(() -> new GeneralException(ErrorCode.USER_NOT_FOUND));

            if (user.getPointBalance() < amount) {
                payment.fail();
                paymentRepository.save(payment);
                throw new GeneralException(ErrorCode.INSUFFICIENT_POINTS);
            }

            user.deductPoints(amount);
            payment.approve("POINT-" + payment.hashCode());
        } else if (method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.Y_PAY) {
            PgPaymentResult result = pgClient.pay(method, amount);

            if (!result.success()) {
                payment.fail();
                paymentRepository.save(payment);
                throw new GeneralException(ErrorCode.PAYMENT_FAILED);
            }

            payment.approve(result.transactionId());
        }

        return paymentRepository.save(payment);
    }
}