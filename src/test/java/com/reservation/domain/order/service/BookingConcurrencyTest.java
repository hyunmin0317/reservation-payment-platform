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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConcurrencyTest extends IntegrationTestSupport {

    private static final int TOTAL_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 100;

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

    private Product product;
    private List<User> users;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "동시성 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, TOTAL_STOCK));
        redisStockService.initStock(product.getId(), TOTAL_STOCK);

        users = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            users.add(userRepository.saveAndFlush(
                    User.create("유저" + i, "user" + i + "@test.com", 200000)
            ));
        }
    }

    @DisplayName("동시 예약 요청 시 재고 수량만큼만 성공하고 초과판매가 발생하지 않는다")
    @Test
    void concurrentBookingPreventsOverselling() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    BookingRequest request = new BookingRequest(product.getId(), List.of(
                            new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
                    ));
                    bookingService.book(users.get(idx).getId(), UUID.randomUUID().toString(), request);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        startLatch.countDown();

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                successCount++;
            }
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount).isEqualTo(TOTAL_STOCK);
        assertThat(orderRepository.count()).isEqualTo(TOTAL_STOCK);
    }
}
