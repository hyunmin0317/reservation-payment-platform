package com.reservation.domain.stock.service;

import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RedisStockService {

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> decreaseStockScript;
    private final StockService stockService;
    private final CircuitBreaker circuitBreaker;

    public RedisStockService(RedisTemplate<String, String> redisTemplate,
                             RedisScript<Long> decreaseStockScript,
                             StockService stockService,
                             CircuitBreakerRegistry circuitBreakerRegistry) {
        this.redisTemplate = redisTemplate;
        this.decreaseStockScript = decreaseStockScript;
        this.stockService = stockService;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
    }

    public boolean decrease(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            Long result = circuitBreaker.executeSupplier(() ->
                    redisTemplate.execute(decreaseStockScript, List.of(key)));

            if (result == -1L) {
                throw new GeneralException(ErrorCode.STOCK_NOT_FOUND);
            }
            if (result == 0L) {
                throw new GeneralException(ErrorCode.STOCK_SOLD_OUT);
            }
            return true;
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis 장애 감지, DB Fallback으로 전환: {}", e.getMessage());
            stockService.decreaseWithPessimisticLock(productId);
            return false;
        }
    }

    public void increase(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            circuitBreaker.executeRunnable(() ->
                    redisTemplate.opsForValue().increment(key));
        } catch (Exception e) {
            log.warn("Redis 장애로 재고 복구 생략 (DB 트랜잭션 롤백으로 처리): {}", e.getMessage());
        }
    }

    public int getRemainingStock(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            String value = circuitBreaker.executeSupplier(() -> redisTemplate.opsForValue().get(key));
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("Redis 재고 조회 실패, DB Fallback으로 재고 조회. productId={}, message={}", productId, e.getMessage());
        }
        return stockService.getRemainingQuantity(productId);
    }

    public void initStock(Long productId, int quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(quantity));
    }
}
