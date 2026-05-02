package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
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

class BookingPointConcurrencyTest extends IntegrationTestSupport {

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

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 100000));
        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 50000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "포인트 동시성 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);
    }

    @DisplayName("동일 사용자가 포인트로 동시에 주문하면 잔액이 음수가 되지 않는다")
    @Test
    void concurrentPointPaymentPreventsNegativeBalance() throws Exception {
        int concurrentRequests = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    BookingRequest request = new BookingRequest(product.getId(), List.of(
                            new PaymentRequest(PaymentMethod.Y_POINT, 50000)
                    ));
                    bookingService.book(user.getId(), UUID.randomUUID().toString(), request);
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

        // 100000 포인트, 50000원 상품 → 최대 2건 성공 가능 (락 경합으로 더 적을 수 있음)
        assertThat(successCount).isLessThanOrEqualTo(2);
        assertThat(successCount).isGreaterThanOrEqualTo(1);

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getPointBalance()).isGreaterThanOrEqualTo(0);
        assertThat(updatedUser.getPointBalance()).isEqualTo(100000 - (successCount * 50000));
    }
}