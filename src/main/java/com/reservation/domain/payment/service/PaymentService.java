package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.dto.PaymentRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PgClient pgClient;

    @Transactional
    public List<Payment> pay(Order order, List<PaymentRequest> requests) {
        // 조합 검증
        Set<PaymentMethod> methods = requests.stream()
                .map(PaymentRequest::paymentMethod)
                .collect(Collectors.toSet());

        if (methods.contains(PaymentMethod.CREDIT_CARD) && methods.contains(PaymentMethod.Y_PAY)) {
            throw new GeneralException(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }

        // 포인트 먼저, PG 나중에 처리하기 위해 분리
        List<PaymentRequest> sorted = new ArrayList<>();
        for (PaymentRequest request : requests) {
            if (request.paymentMethod() == PaymentMethod.Y_POINT) {
                sorted.add(0, request);
            } else {
                sorted.add(request);
            }
        }

        List<Payment> completedPayments = new ArrayList<>();

        try {
            for (PaymentRequest request : sorted) {
                Payment payment = Payment.builder()
                        .order(order)
                        .paymentMethod(request.paymentMethod())
                        .amount(request.amount())
                        .build();

                if (request.paymentMethod() == PaymentMethod.Y_POINT) {
                    User user = userRepository.findById(order.getUser().getId())
                            .orElseThrow(() -> new GeneralException(ErrorCode.USER_NOT_FOUND));

                    if (user.getPointBalance() < request.amount()) {
                        payment.fail();
                        paymentRepository.save(payment);
                        throw new GeneralException(ErrorCode.INSUFFICIENT_POINTS);
                    }

                    user.deductPoints(request.amount());
                    payment.approve(UUID.randomUUID().toString());

                } else if (request.paymentMethod() == PaymentMethod.CREDIT_CARD
                        || request.paymentMethod() == PaymentMethod.Y_PAY) {
                    PgPaymentResult result = pgClient.pay(request.paymentMethod(), request.amount());

                    if (!result.success()) {
                        payment.fail();
                        paymentRepository.save(payment);
                        throw new GeneralException(ErrorCode.PAYMENT_FAILED);
                    }

                    payment.approve(result.transactionId());
                }

                paymentRepository.save(payment);
                completedPayments.add(payment);
            }
        } catch (GeneralException e) {
            // 보상 트랜잭션: 이미 완료된 결제 롤백
            for (Payment completed : completedPayments) {
                if (completed.getPaymentMethod() == PaymentMethod.Y_POINT) {
                    User user = userRepository.findById(order.getUser().getId()).orElseThrow();
                    user.restorePoints(completed.getAmount());
                } else {
                    pgClient.cancel(completed.getTransactionId());
                }
                completed.cancel();
                paymentRepository.save(completed);
            }
            throw e;
        }

        return completedPayments;
    }
}