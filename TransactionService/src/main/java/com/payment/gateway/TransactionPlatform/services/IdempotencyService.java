package com.payment.gateway.TransactionPlatform.services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final String KEY_PREFIX = "idempotency:payment:";
    private static final long DEFAULT_TTL_SECONDS = Duration.ofMinutes(30).getSeconds();
    
    // Lua scripts cached as static constants to avoid re-instantiation per call
    private static final String LUA_FINALIZE = "local v = redis.call('get', KEYS[1]); if v == ARGV[1] then redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1; else return 0; end";
    private static final String LUA_MARK_FAILED = "local v = redis.call('get', KEYS[1]); if v == ARGV[1] then redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1; else return 0; end";
    private static final DefaultRedisScript<Long> FINALIZE_SCRIPT = createLongScript(LUA_FINALIZE);
    private static final DefaultRedisScript<Long> MARK_FAILED_SCRIPT = createLongScript(LUA_MARK_FAILED);

    /**
     * Returns true if the key already exists (i.e. it's a duplicate request). The caller usually
     * sets the key to "PROCESSING" using setIfAbsent before processing.
     */
    public boolean isDuplicate(String key) {
        return Boolean.FALSE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "PROCESSING", Duration.ofMinutes(30))
        );
    }

    /**
     * Atomically set the final response only if the current value is exactly "PROCESSING".
     * Returns true if the value was updated.
     */
    public boolean finalizeResponseIfProcessing(String key, String responseJson) {
        Long result = redisTemplate.execute(FINALIZE_SCRIPT, Collections.singletonList(KEY_PREFIX + key), "PROCESSING", responseJson, String.valueOf(DEFAULT_TTL_SECONDS));
        return result != null && result == 1L;
    }

    /**
     * Mark the idempotency key as failed with a reason, but only if it was PROCESSING.
     * Returns true if updated.
     */
    public boolean markFailedIfProcessing(String key, String reason) {
        String failedValue = "FAILED:" + (reason == null ? "" : reason);
        Long result = redisTemplate.execute(MARK_FAILED_SCRIPT, Collections.singletonList(KEY_PREFIX + key), "PROCESSING", failedValue, String.valueOf(DEFAULT_TTL_SECONDS));
        return result != null && result == 1L;
    }

    private static DefaultRedisScript<Long> createLongScript(String script) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    /**
     * Forcefully overwrite the record (used for idempotency restore/admin actions).
     */
    public void overwriteRecord(String key, String value) {
        redisTemplate.opsForValue().set(KEY_PREFIX + key, value, Duration.ofMinutes(30));
    }

    public String getPreviousResponse(String key) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + key);
    }

    public void deleteKey(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}