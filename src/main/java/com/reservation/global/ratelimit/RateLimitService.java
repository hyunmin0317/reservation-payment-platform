package com.reservation.global.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:user:";
    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(Long userId) {
        try {
            String key = KEY_PREFIX + userId;
            Long count = redisTemplate.opsForValue().increment(key);

            if (count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }

            return count <= MAX_REQUESTS;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 Rate Limiting 비활성화: {}", e.getMessage());
            return true;
        }
    }
}
