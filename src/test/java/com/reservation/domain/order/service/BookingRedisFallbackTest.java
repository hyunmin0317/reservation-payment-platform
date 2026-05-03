package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.entity.OrderStatus;
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
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
class BookingRedisFallbackTest {

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("reservation_fallback_test");

    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "1");
    }

    @MockBean
    private PgClient pgClient;

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

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 100000));
        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "Fallback 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
    }

    @DisplayName("Redis 장애 시 DB Fallback으로 예약이 정상 처리된다")
    @Test
    void bookingSucceedsWithDbFallback() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.success("txn-fallback-001"));

        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        BookingResponse response = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.totalAmount()).isEqualTo(100000);

        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getRemainingQuantity()).isEqualTo(9);
    }

    @DisplayName("Redis 장애 시 결제 실패하면 DB 재고가 복구된다")
    @Test
    void dbStockRestoredWhenPaymentFailsInFallback() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.failure(ErrorCode.PAYMENT_FAILED));

        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_FAILED));

        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getRemainingQuantity()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @DisplayName("Redis 장애 시 동일 멱등성 키로 재요청하면 기존 주문을 반환한다")
    @Test
    void idempotencyFallbackReturnsSameOrder() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.success("txn-fallback-002"));

        String idempotencyKey = UUID.randomUUID().toString();
        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        BookingResponse first = bookingService.book(user.getId(), idempotencyKey, request);
        BookingResponse second = bookingService.book(user.getId(), idempotencyKey, request);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @DisplayName("Redis 장애 시 재고 수량만큼만 예약이 성공한다")
    @Test
    void fallbackPreventsOverselling() {
        when(pgClient.pay(anyInt())).thenReturn(PaymentResult.success("txn-fallback-003"));

        stockRepository.deleteAll();
        stockRepository.saveAndFlush(Stock.create(product, 2));

        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            try {
                User u = userRepository.saveAndFlush(
                        User.create("유저" + i, "user" + i + "@test.com", 200000));
                BookingRequest request = new BookingRequest(product.getId(), List.of(
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
                ));
                bookingService.book(u.getId(), UUID.randomUUID().toString(), request);
                successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(2);
        Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getRemainingQuantity()).isZero();
    }
}
