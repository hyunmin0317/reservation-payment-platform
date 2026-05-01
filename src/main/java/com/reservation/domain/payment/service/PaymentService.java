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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Map<PaymentMethod, PaymentStrategy> strategyMap;

    public PaymentService(PaymentRepository paymentRepository, List<PaymentStrategy> strategies) {
        this.paymentRepository = paymentRepository;
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(PaymentStrategy::getPaymentMethod, Function.identity()));
    }

    @Transactional
    public List<Payment> pay(Order order, List<PaymentRequest> requests) {
        validateCombination(requests);

        List<PaymentRequest> sorted = sortInternalFirst(requests);
        List<Payment> completedPayments = new ArrayList<>();

        try {
            for (PaymentRequest request : sorted) {
                Payment payment = Payment.builder()
                        .order(order)
                        .paymentMethod(request.paymentMethod())
                        .amount(request.amount())
                        .build();

                PaymentStrategy strategy = strategyMap.get(request.paymentMethod());
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

    private void validateCombination(List<PaymentRequest> requests) {
        long externalCount = requests.stream()
                .filter(r -> r.paymentMethod().getType() == PaymentMethodType.EXTERNAL)
                .count();

        if (externalCount > 1) {
            throw new GeneralException(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }
    }

    private List<PaymentRequest> sortInternalFirst(List<PaymentRequest> requests) {
        return requests.stream()
                .sorted(Comparator.comparing(r -> r.paymentMethod().getType() == PaymentMethodType.INTERNAL ? 0 : 1))
                .collect(Collectors.toList());
    }

    private void rollback(List<Payment> completedPayments, Order order) {
        for (Payment completed : completedPayments) {
            PaymentStrategy strategy = strategyMap.get(completed.getPaymentMethod());
            strategy.cancel(completed, order);
            completed.cancel();
            paymentRepository.save(completed);
        }
    }
}