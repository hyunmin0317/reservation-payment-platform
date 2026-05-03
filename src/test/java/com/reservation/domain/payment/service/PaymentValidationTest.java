package com.reservation.domain.payment.service;

import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static com.reservation.domain.payment.entity.PaymentMethod.PaymentMethodType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentValidationTest {

    @DisplayName("결제 금액 합계가 상품 가격과 일치하지 않으면 예외 발생")
    @Test
    void invalidTotalAmount() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000)
        );

        assertThatThrownBy(() -> validateTotalAmount(requests, 100000))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PAYMENT_AMOUNT));
    }

    @DisplayName("결제 금액 합계가 일치하면 통과")
    @Test
    void validTotalAmount() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000),
                new PaymentRequest(PaymentMethod.Y_POINT, 30000)
        );

        validateTotalAmount(requests, 100000);
    }

    @DisplayName("외부 결제 수단 2개 이상 사용 시 예외 발생")
    @Test
    void invalidCombinationTwoExternal() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000),
                new PaymentRequest(PaymentMethod.Y_PAY, 50000)
        );

        assertThatThrownBy(() -> validateCombination(requests))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION));
    }

    @DisplayName("외부 1개 + 포인트 조합은 허용")
    @Test
    void validCombinationExternalPlusPoint() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000),
                new PaymentRequest(PaymentMethod.Y_POINT, 30000)
        );

        validateCombination(requests);
    }

    @DisplayName("포인트 단독 사용 허용")
    @Test
    void validCombinationPointOnly() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.Y_POINT, 100000)
        );

        validateCombination(requests);
    }

    @DisplayName("INTERNAL 결제가 먼저 정렬된다")
    @Test
    void internalFirstSorting() {
        List<PaymentRequest> requests = List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000),
                new PaymentRequest(PaymentMethod.Y_POINT, 30000)
        );

        List<PaymentRequest> sorted = sortInternalFirst(requests);

        assertThat(sorted.get(0).method()).isEqualTo(PaymentMethod.Y_POINT);
        assertThat(sorted.get(1).method()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    // PaymentService의 private 메서드를 단위 테스트용으로 재현
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
}
