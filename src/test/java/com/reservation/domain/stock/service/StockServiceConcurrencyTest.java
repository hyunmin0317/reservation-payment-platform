package com.reservation.domain.stock.service;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.repository.StockRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StockServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("reservation_test");

    private static final int TOTAL_STOCK = 10;

    @Autowired
    private StockService stockService;

    @Autowired
    private StockFacade stockFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();
        productRepository.deleteAll();

        Product product = Product.create(
                "테스트 숙소",
                100000,
                LocalTime.of(15, 0),
                LocalTime.of(11, 0),
                "동시성 테스트용 상품"
        );
        Product savedProduct = productRepository.saveAndFlush(product);

        stockRepository.saveAndFlush(Stock.create(savedProduct, TOTAL_STOCK));

        productId = savedProduct.getId();
    }

    private void runConcurrencyTest(String label, Runnable operation, int requestCount) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(64);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            tasks.add(() -> {
                startLatch.await();
                long start = System.nanoTime();
                try {
                    operation.run();
                    latencies.add(System.nanoTime() - start);
                    return true;
                } catch (GeneralException exception) {
                    latencies.add(System.nanoTime() - start);
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STOCK_SOLD_OUT);
                    return false;
                }
            });
        }

        List<Future<Boolean>> futures = tasks.stream()
                .map(executorService::submit)
                .toList();

        long startedAt = System.nanoTime();
        startLatch.countDown();

        int successCount = 0;
        int failureCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                successCount++;
            } else {
                failureCount++;
            }
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        assertThat(successCount).isEqualTo(TOTAL_STOCK);
        assertThat(failureCount).isEqualTo(requestCount - TOTAL_STOCK);
        assertThat(stock.getRemainingQuantity()).isZero();

        List<Long> sortedLatencies = latencies.stream().sorted().toList();
        long avgMs = sortedLatencies.stream().mapToLong(Long::longValue).sum() / sortedLatencies.size() / 1_000_000;
        long maxMs = sortedLatencies.get(sortedLatencies.size() - 1) / 1_000_000;
        long p95Ms = sortedLatencies.get((int) (sortedLatencies.size() * 0.95)) / 1_000_000;
        long p99Ms = sortedLatencies.get((int) (sortedLatencies.size() * 0.99)) / 1_000_000;

        System.out.printf(
                "%s: requests=%d, success=%d, failure=%d, totalMs=%d, avgMs=%d, p95Ms=%d, p99Ms=%d, maxMs=%d%n",
                label, requestCount, successCount, failureCount, elapsedMillis, avgMs, p95Ms, p99Ms, maxMs
        );
    }

    @Nested
    class PessimisticLock {
        @ParameterizedTest
        @ValueSource(ints = {100, 1000})
        void preventsOverselling(int requestCount) throws Exception {
            runConcurrencyTest("Pessimistic lock",
                    () -> stockService.decreaseWithPessimisticLock(productId), requestCount);
        }
    }

    @Nested
    class OptimisticLock {
        @ParameterizedTest
        @ValueSource(ints = {100, 1000})
        void preventsOverselling(int requestCount) throws Exception {
            runConcurrencyTest("Optimistic lock (retry=100)",
                    () -> stockFacade.decreaseWithOptimisticLock(productId, 100), requestCount);
        }
    }
}
