package com.reservation.domain.stock.service;

import com.reservation.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockInitializer implements ApplicationRunner {

    private final StockRepository stockRepository;
    private final RedisStockService redisStockService;

    @Override
    public void run(ApplicationArguments args) {
        stockRepository.findAll().forEach(stock -> {
            redisStockService.initStock(stock.getProduct().getId(), stock.getRemainingQuantity());
            log.info("Redis 재고 초기화: productId={}, quantity={}", stock.getProduct().getId(), stock.getRemainingQuantity());
        });
    }
}
