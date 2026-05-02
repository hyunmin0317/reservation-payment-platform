package com.reservation.domain.order.service;

import com.reservation.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;

    public boolean tryAcquire(String idempotencyKey) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + idempotencyKey, "1", TTL);
            return Boolean.TRUE.equals(result);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애 감지, DB Fallback으로 멱등성 체크: {}", e.getMessage());
            return orderRepository.findByIdempotencyKey(idempotencyKey).isEmpty();
        }
    }

    public void release(String idempotencyKey) {
        try {
            redisTemplate.delete(KEY_PREFIX + idempotencyKey);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 멱등성 키 삭제 생략: {}", e.getMessage());
        }
    }
}
