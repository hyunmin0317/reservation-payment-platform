package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.repository.OrderRepository;
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
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIdempotencyTest extends IntegrationTestSupport {

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
                "멱등성 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);
    }

    @DisplayName("동일 멱등성 키로 재요청 시 기존 결과를 반환한다")
    @Test
    void duplicateRequestReturnsSameResult() {
        String idempotencyKey = UUID.randomUUID().toString();
        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        BookingResponse first = bookingService.book(user.getId(), idempotencyKey, request);
        BookingResponse second = bookingService.book(user.getId(), idempotencyKey, request);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.orderNumber()).isEqualTo(first.orderNumber());
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @DisplayName("다른 멱등성 키로 요청 시 별도 주문이 생성된다")
    @Test
    void differentKeysCreateSeparateOrders() {
        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        BookingResponse first = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);
        BookingResponse second = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(orderRepository.count()).isEqualTo(2);
    }
}
