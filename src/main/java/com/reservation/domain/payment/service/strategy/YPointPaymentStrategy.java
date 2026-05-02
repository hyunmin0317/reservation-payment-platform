package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class YPointPaymentStrategy implements PaymentStrategy {

    private final UserRepository userRepository;

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.Y_POINT;
    }

    @Override
    public void pay(Payment payment, Order order) {
        User user = userRepository.findWithLockById(order.getUser().getId())
                .orElseThrow(() -> new GeneralException(ErrorCode.USER_NOT_FOUND));

        if (user.getPointBalance() < payment.getAmount()) {
            payment.fail();
            throw new GeneralException(ErrorCode.INSUFFICIENT_POINTS);
        }

        user.deductPoints(payment.getAmount());
        payment.approve(UUID.randomUUID().toString());
    }

    @Override
    public void cancel(Payment payment, Order order) {
        User user = userRepository.findById(order.getUser().getId()).orElseThrow();
        user.restorePoints(payment.getAmount());
    }
}