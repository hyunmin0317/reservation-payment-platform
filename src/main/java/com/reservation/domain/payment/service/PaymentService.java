package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentMethod.PaymentMethodType;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.payment.service.strategy.PaymentStrategy;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Map<PaymentMethod, PaymentStrategy> strategyMap;

    public PaymentService(PaymentRepository paymentRepository, List<PaymentStrategy> strategies) {
        this.paymentRepository = paymentRepository;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(PaymentStrategy::getMethod, Function.identity()));
    }

    @Transactional
    public List<Payment> pay(Order order, List<PaymentRequest> requests, int productPrice) {
        validateTotalAmount(requests, productPrice);
        validateCombination(requests);

        List<PaymentRequest> sorted = sortInternalFirst(requests);
        List<Payment> completedPayments = new ArrayList<>();

        try {
            for (PaymentRequest request : sorted) {
                Payment payment = Payment.builder()
                        .order(order)
                        .method(request.method())
                        .amount(request.amount())
                        .build();

                PaymentStrategy strategy = strategyMap.get(request.method());
                strategy.pay(payment, order);

                paymentRepository.save(payment);
                completedPayments.add(payment);
            }
        } catch (GeneralException e) {
            rollback(completedPayments, order);
            throw e;
        }

        return completedPayments;
    }

    private void validateTotalAmount(List<PaymentRequest> requests, int productPrice) {
        int totalPayment = requests.stream().mapToInt(PaymentRequest::amount).sum();
        if (totalPayment != productPrice) {
            throw new GeneralException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
    }

    private void validateCombination(List<PaymentRequest> requests) {
        long externalCount = requests.stream()
                .filter(r -> r.method().getType() == PaymentMethodType.EXTERNAL)
                .count();

        if (externalCount > 1) {
            throw new GeneralException(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }
    }

    private List<PaymentRequest> sortInternalFirst(List<PaymentRequest> requests) {
        return requests.stream()
                .sorted(Comparator.comparing(r -> r.method().getType() == PaymentMethodType.INTERNAL ? 0 : 1))
                .toList();
    }

    private void rollback(List<Payment> completedPayments, Order order) {
        for (Payment completed : completedPayments) {
            try {
                PaymentStrategy strategy = strategyMap.get(completed.getMethod());
                strategy.cancel(completed, order);
                completed.cancel();
                paymentRepository.save(completed);
            } catch (Exception e) {
                log.error("결제 취소 실패 (paymentId: {}, method: {}): {}",
                        completed.getId(), completed.getMethod(), e.getMessage());
            }
        }
    }
}