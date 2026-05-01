package com.reservation.domain.stock.service;

import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisStockService {

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> decreaseStockScript;

    public void decrease(Long productId) {
        String key = STOCK_KEY_PREFIX + productId;
        Long result = redisTemplate.execute(decreaseStockScript, List.of(key));

        if (result == 0L) {
            throw new GeneralException(ErrorCode.STOCK_SOLD_OUT);
        }
    }

    public void initStock(Long productId, int quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(quantity));
    }
}