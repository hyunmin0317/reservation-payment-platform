package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.client.PaymentResult;
import com.reservation.domain.payment.client.PgClient;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.repository.StockRepository;
import com.reservation.domain.stock.service.RedisStockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class BookingCompensationTest extends IntegrationTestSupport {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RedisStockService redisStockService;

    @MockBean
    private PgClient pgClient;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));
        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "보상 트랜잭션 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);
    }

    @DisplayName("카드 결제 실패 시 Redis 재고가 복구된다")
    @Test
    void stockRestoredWhenPaymentFails() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.failure(ErrorCode.PAYMENT_FAILED));

        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_FAILED));

        assertThat(redisStockService.getRemainingStock(product.getId())).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @DisplayName("복합결제(카드+포인트)에서 카드 실패 시 포인트가 환불되고 재고가 복구된다")
    @Test
    void pointRefundedAndStockRestoredWhenCardFails() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.failure(ErrorCode.PAYMENT_LIMIT_EXCEEDED));

        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.Y_POINT, 30000),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000)
        ));

        assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_LIMIT_EXCEEDED));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getPointBalance()).isEqualTo(50000);

        assertThat(redisStockService.getRemainingStock(product.getId())).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @DisplayName("카드 결제 예외 시 재고가 복구되고 멱등성 키가 해제되어 재시도 가능하다")
    @Test
    void retryAllowedAfterPaymentFailure() {
        when(pgClient.pay(anyInt()))
                .thenThrow(new RuntimeException("Connection timeout"))
                .thenReturn(PaymentResult.success("txn-001"));

        String idempotencyKey = UUID.randomUUID().toString();
        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        assertThatThrownBy(() -> bookingService.book(user.getId(), idempotencyKey, request))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_FAILED));

        assertThat(redisStockService.getRemainingStock(product.getId())).isEqualTo(10);

        var response = bookingService.book(user.getId(), idempotencyKey, request);
        assertThat(response.orderStatus()).isEqualTo(com.reservation.domain.order.entity.OrderStatus.COMPLETED);
        assertThat(redisStockService.getRemainingStock(product.getId())).isEqualTo(9);
    }
}
