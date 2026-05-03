package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.payment.service.strategy.PaymentStrategy;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentServiceUnsupportedStrategyTest {

    @DisplayName("결제 수단에 해당하는 Strategy가 없으면 명시적인 예외가 발생한다")
    @Test
    void unsupportedPaymentMethodFails() {
        PaymentService paymentService = new PaymentService(
                mock(PaymentRepository.class),
                List.of(new StubPaymentStrategy(PaymentMethod.CREDIT_CARD))
        );

        assertThatThrownBy(() -> paymentService.pay(
                mock(Order.class),
                List.of(new PaymentRequest(PaymentMethod.Y_PAY, 100000)),
                100000
        ))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_PAYMENT_METHOD));
    }

    private record StubPaymentStrategy(PaymentMethod method) implements PaymentStrategy {

        @Override
        public PaymentMethod getMethod() {
            return method;
        }

        @Override
        public void pay(Payment payment, Order order) {
            payment.approve("test-transaction-id");
        }

        @Override
        public void cancel(Payment payment, Order order) {
        }
    }
}
