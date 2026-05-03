package com.reservation.global.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:user:";
    private static final int MAX_REQUESTS = 5;
    private static final int WINDOW_SECONDS = 1;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final ConcurrentHashMap<Long, AtomicInteger> localLimitMap = new ConcurrentHashMap<>();
    private volatile long localWindowStart = System.currentTimeMillis();

    public boolean isAllowed(Long userId) {
        try {
            String key = KEY_PREFIX + userId;
            Long count = redisTemplate.execute(rateLimitScript, List.of(key), String.valueOf(WINDOW_SECONDS));
            return count != null && count <= MAX_REQUESTS;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 로컬 Rate Limiting 적용: {}", e.getMessage());
            return isAllowedLocal(userId);
        }
    }

    private boolean isAllowedLocal(Long userId) {
        long now = System.currentTimeMillis();
        if (now - localWindowStart > WINDOW_SECONDS * 1000L) {
            localLimitMap.clear();
            localWindowStart = now;
        }
        AtomicInteger counter = localLimitMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_REQUESTS;
    }
}
