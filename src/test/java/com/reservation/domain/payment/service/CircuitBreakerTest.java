package com.reservation.domain.payment.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.client.PaymentResult;
import com.reservation.domain.payment.client.PgClient;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import com.reservation.support.IntegrationTestSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CircuitBreakerTest extends IntegrationTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PgClient pgClient;

    private Order order;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            cb.transitionToClosedState();
            cb.reset();
        });

        User user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));
        Product product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "서킷브레이커 테스트용 상품"
        ));
        order = orderRepository.saveAndFlush(Order.builder()
                .orderNumber("ORD-TEST-CB")
                .user(user)
                .product(product)
                .totalAmount(100000)
                .idempotencyKey("cb-test-key")
                .build());
    }

    @DisplayName("PG 결제 실패 시 PAYMENT_FAILED 반환")
    @Test
    void paymentFailureReturnsError() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.failure(ErrorCode.PAYMENT_FAILED));

        assertThatThrownBy(() -> paymentService.pay(order, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        )))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_FAILED));
    }

    @DisplayName("PG 한도 초과 시 PAYMENT_LIMIT_EXCEEDED 반환")
    @Test
    void limitExceededReturnsError() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.failure(ErrorCode.PAYMENT_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> paymentService.pay(order, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        )))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_LIMIT_EXCEEDED));
    }

    @DisplayName("PG 타임아웃 시 PAYMENT_TIMEOUT 반환")
    @Test
    void timeoutReturnsError() {
        when(pgClient.pay(anyInt())).thenThrow(new RuntimeException("Connection timeout"));

        assertThatThrownBy(() -> paymentService.pay(order, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        )))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_TIMEOUT));
    }

    @DisplayName("연속 실패 시 서킷이 OPEN되고 PAYMENT_SERVICE_UNAVAILABLE 반환")
    @Test
    void circuitOpensAfterConsecutiveFailures() {
        when(pgClient.pay(anyInt())).thenThrow(new RuntimeException("PG down"));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("externalPayment");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        int failureCount = 0;
        for (int i = 0; i < 10; i++) {
            try {
                paymentService.pay(order, List.of(
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
                ));
            } catch (GeneralException e) {
                failureCount++;
                System.out.println("[CB-TEST] iteration=" + i + " errorCode=" + e.getErrorCode() + " cbState=" + cb.getState() + " metrics=" + cb.getMetrics().getNumberOfFailedCalls());
                if (cb.getState() == CircuitBreaker.State.OPEN) {
                    break;
                }
            } catch (Exception e) {
                System.out.println("[CB-TEST] iteration=" + i + " unexpected=" + e.getClass().getName() + " msg=" + e.getMessage());
            }
        }

        System.out.println("[CB-TEST] final failureCount=" + failureCount + " cbState=" + cb.getState() + " metrics=" + cb.getMetrics().getNumberOfFailedCalls());
        assertThat(failureCount).isGreaterThanOrEqualTo(5);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> paymentService.pay(order, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        )))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE));
    }
}