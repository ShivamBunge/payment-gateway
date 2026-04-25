package com.payment.gateway.TransactionPlatform.services;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Service responsible for idempotency key lifecycle in Redis.
 *
 * Implementation notes (why Lua is used):
 * - Several operations require an atomic "compare current value then set with TTL". Using a small
 *   Redis Lua script ensures that the entire read-check-write-with-expiry sequence executes on the
 *   Redis server as a single atomic unit (no intermediate client round-trips or races).
 * - The Lua scripts below follow the pattern:
 *     local v = redis.call('get', KEYS[1]);
 *     if v == ARGV[1] then
 *         redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]);
 *         return 1;
 *     else
 *         return 0;
 *     end
 *   where:
 *     KEYS[1] = the Redis key (prefix + idempotencyId)
 *     ARGV[1] = expected current value (e.g. "PROCESSING")
 *     ARGV[2] = new value to set (e.g. final JSON or "FAILED:reason")
 *     ARGV[3] = TTL in seconds
 * - This returns 1 when the compare-and-set succeeded, 0 otherwise.
 *
 * Why keep Lua: it's compact, fast, and avoids complex retry logic. The script is intentionally
 * tiny and documented here so developers unfamiliar with Redis can understand the reason it
 * exists without needing deep Lua knowledge.
 */
@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Key namespace. Consider moving to application.yml for configurability if teams prefer.
    private static final String KEY_PREFIX = "idempotency:payment:";
    // Default TTL applied to keys when set. Consider making configurable via @Value.
    private static final long DEFAULT_TTL_SECONDS = Duration.ofMinutes(30).getSeconds();
    
    // Lua script used to atomically replace a value only when it matches an expected value,
    // and to set an expiry at the same time. Kept as a String constant for readability and
    // to avoid re-creating the script object on each invocation.
    // ARGV mapping (consistent across uses):
    //  ARGV[1] = expected current value
    //  ARGV[2] = new value to set
    //  ARGV[3] = ttl seconds
    private static final String LUA_FINALIZE = "local v = redis.call('get', KEYS[1]); if v == ARGV[1] then redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1; else return 0; end";
    // MARK_FAILED uses the same logic as FINALIZE (compare-and-set-with-expiry). We keep a
    // separate constant for semantic clarity in code, but the script text is identical.
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
     *
     * This uses the LUA_FINALIZE script. Arguments passed to the script are:
     *  - expected value: "PROCESSING"
     *  - new value: responseJson
     *  - ttl seconds: DEFAULT_TTL_SECONDS
     */
    public boolean finalizeResponseIfProcessing(String key, String responseJson) {
        Long result = redisTemplate.execute(FINALIZE_SCRIPT, Collections.singletonList(KEY_PREFIX + key), "PROCESSING", responseJson, String.valueOf(DEFAULT_TTL_SECONDS));
        return result != null && result == 1L;
    }

    /**
     * Mark the idempotency key as failed with a reason, but only if it was PROCESSING.
     * Returns true if updated.
     *
     * This uses the LUA_MARK_FAILED script with the same ARGV contract as above.
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