package com.reservation.domain.stock.service;

import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStockService {

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> decreaseStockScript;
    private final StockService stockService;

    public boolean decrease(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            Long result = redisTemplate.execute(decreaseStockScript, List.of(key));

            if (result == 0L) {
                throw new GeneralException(ErrorCode.STOCK_SOLD_OUT);
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애 감지, DB Fallback으로 전환: {}", e.getMessage());
            stockService.decreaseWithPessimisticLock(productId);
            return false;
        }
    }

    public void increase(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            redisTemplate.opsForValue().increment(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 재고 복구 생략 (DB 트랜잭션 롤백으로 처리): {}", e.getMessage());
        }
    }

    public int getRemainingStock(Long productId) {
        try {
            String key = STOCK_KEY_PREFIX + productId;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애 감지, DB Fallback으로 재고 조회: {}", e.getMessage());
            return stockService.getStockByProductId(productId).getRemainingQuantity();
        }
    }

    public void initStock(Long productId, int quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(quantity));
    }
}
