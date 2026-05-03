package com.reservation.global.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:user:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final int maxRequests;
    private final int windowSeconds;
    private final ConcurrentHashMap<Long, AtomicInteger> localLimitMap = new ConcurrentHashMap<>();
    private volatile long localWindowStart = System.currentTimeMillis();

    public RateLimitService(StringRedisTemplate redisTemplate,
                            RedisScript<Long> rateLimitScript,
                            @Value("${rate-limit.max-requests:5}") int maxRequests,
                            @Value("${rate-limit.window-seconds:1}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean isAllowed(Long userId) {
        try {
            String key = KEY_PREFIX + userId;
            Long count = redisTemplate.execute(rateLimitScript, List.of(key), String.valueOf(windowSeconds));
            return count != null && count <= maxRequests;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 장애로 로컬 Rate Limiting 적용: {}", e.getMessage());
            return isAllowedLocal(userId);
        }
    }

    private boolean isAllowedLocal(Long userId) {
        long now = System.currentTimeMillis();
        if (now - localWindowStart > windowSeconds * 1000L) {
            localLimitMap.clear();
            localWindowStart = now;
        }
        AtomicInteger counter = localLimitMap.computeIfAbsent(userId, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= maxRequests;
    }
}
