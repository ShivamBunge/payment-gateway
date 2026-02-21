package com.payment.gateway.TransactionPlatform.services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    private static final String KEY_PREFIX = "idempotency:payment:";

    public boolean isDuplicate(String key) {
        // Returns true if the key ALREADY exists
        return Boolean.FALSE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "PROCESSING", Duration.ofMinutes(30))
        );
    }

    public void updateRecord(String key, String responseJson) {
        redisTemplate.opsForValue().set(KEY_PREFIX + key, responseJson, Duration.ofMinutes(30));
    }

    public String getPreviousResponse(String key) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + key);
    }
}