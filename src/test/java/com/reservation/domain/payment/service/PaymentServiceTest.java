package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;

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

    private Order order;
    private User user;

    @Autowired
    private com.reservation.domain.payment.repository.PaymentRepository paymentRepository;

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

    @DisplayName("신용카드 결제 성공")
    @Test
    void creditCardPaymentSucceeds() {
        Payment payment = paymentService.pay(order, PaymentMethod.CREDIT_CARD, 100000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).startsWith("TXN-");
    }

    @DisplayName("Y페이 결제 성공")
    @Test
    void yPayPaymentSucceeds() {
        Payment payment = paymentService.pay(order, PaymentMethod.Y_PAY, 100000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).startsWith("TXN-");
    }

    @DisplayName("Y포인트 결제 성공 시 잔액 차감")
    @Test
    void yPointPaymentSucceeds() {
        Payment payment = paymentService.pay(order, PaymentMethod.Y_POINT, 30000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getPointBalance()).isEqualTo(20000);
    }

    @DisplayName("Y포인트 잔액 부족 시 결제 실패")
    @Test
    void yPointPaymentFailsWhenInsufficientBalance() {
        assertThatThrownBy(() -> paymentService.pay(order, PaymentMethod.Y_POINT, 60000))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
    }
}
