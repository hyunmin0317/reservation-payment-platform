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

    public boolean exists(String idempotencyKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애 감지, DB Fallback으로 멱등성 체크: {}", e.getMessage());
            return orderRepository.findByIdempotencyKey(idempotencyKey).isPresent();
        }
    }

    public void save(String idempotencyKey) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, "1", TTL);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 멱등성 키 저장 생략 (DB UNIQUE 제약조건으로 보장): {}", e.getMessage());
        }
    }
}
