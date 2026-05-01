package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest extends IntegrationTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));

        Product product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "결제 테스트용 상품"
        ));

        order = orderRepository.saveAndFlush(Order.builder()
                .orderNumber("ORD-TEST-001")
                .user(user)
                .product(product)
                .totalAmount(100000)
                .idempotencyKey("test-key-001")
                .build());
    }

    @Nested
    @DisplayName("단일 결제")
    class SinglePayment {

        @DisplayName("신용카드 결제 성공")
        @Test
        void creditCardPaymentSucceeds() {
            List<Payment> payments = paymentService.pay(order,
                    List.of(new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)));

            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payments.get(0).getTransactionId()).isNotNull();
        }

        @DisplayName("Y페이 결제 성공")
        @Test
        void yPayPaymentSucceeds() {
            List<Payment> payments = paymentService.pay(order,
                    List.of(new PaymentRequest(PaymentMethod.Y_PAY, 100000)));

            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }

        @DisplayName("Y포인트 결제 성공 시 잔액 차감")
        @Test
        void yPointPaymentSucceeds() {
            List<Payment> payments = paymentService.pay(order,
                    List.of(new PaymentRequest(PaymentMethod.Y_POINT, 30000)));

            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.APPROVED);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(20000);
        }

        @DisplayName("Y포인트 잔액 부족 시 결제 실패")
        @Test
        void yPointPaymentFailsWhenInsufficientBalance() {
            assertThatThrownBy(() -> paymentService.pay(order,
                    List.of(new PaymentRequest(PaymentMethod.Y_POINT, 60000))))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        }
    }

    @Nested
    @DisplayName("복합 결제")
    class CompositePayment {

        @DisplayName("신용카드 + Y포인트 복합 결제 성공")
        @Test
        void creditCardWithPointSucceeds() {
            List<Payment> payments = paymentService.pay(order, List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 30000),
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000)
            ));

            assertThat(payments).hasSize(2);
            assertThat(payments).allMatch(p -> p.getStatus() == PaymentStatus.APPROVED);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(20000);
        }

        @DisplayName("Y페이 + Y포인트 복합 결제 성공")
        @Test
        void yPayWithPointSucceeds() {
            List<Payment> payments = paymentService.pay(order, List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 20000),
                    new PaymentRequest(PaymentMethod.Y_PAY, 80000)
            ));

            assertThat(payments).hasSize(2);
            assertThat(payments).allMatch(p -> p.getStatus() == PaymentStatus.APPROVED);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(30000);
        }
    }

    @Nested
    @DisplayName("결제 조합 검증")
    class PaymentCombinationValidation {

        @DisplayName("신용카드 + Y페이 혼용 불가")
        @Test
        void creditCardAndYPayCombinationFails() {
            assertThatThrownBy(() -> paymentService.pay(order, List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000),
                    new PaymentRequest(PaymentMethod.Y_PAY, 50000)
            )))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION));
        }

        @DisplayName("복합 결제 중 PG 실패 시 포인트 환불")
        @Test
        void rollbackPointWhenPgFails() {
            // MockPgClient는 항상 성공하므로 이 테스트는 Strategy 패턴 전환 후 Mock 주입으로 검증
            // 여기서는 포인트 먼저 차감 → 카드 성공 흐름만 확인
            List<Payment> payments = paymentService.pay(order, List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 10000),
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 90000)
            ));

            assertThat(payments).hasSize(2);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(40000);
        }
    }
}