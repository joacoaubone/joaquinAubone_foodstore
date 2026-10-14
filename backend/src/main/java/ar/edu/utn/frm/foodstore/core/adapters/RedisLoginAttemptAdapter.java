package ar.edu.utn.frm.foodstore.core.adapters;

import ar.edu.utn.frm.foodstore.core.ports.LoginAttemptRedisPort;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLoginAttemptAdapter implements LoginAttemptRedisPort {

    private static final String KEY_PREFIX = "fs:intentos:";
    private static final DefaultRedisScript<Long> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Long currentCount(String email) {
        String count = redisTemplate.opsForValue().get(key(email));
        return count == null ? null : Long.valueOf(count);
    }

    @Override
    public long increment(String email, Duration window) {
        Long count = redisTemplate.execute(
                INCREMENT_AND_EXPIRE,
                List.of(key(email)),
                Long.toString(window.toMillis()));
        if (count == null) {
            throw new IllegalStateException("Redis no devolvió el contador de intentos");
        }
        return count;
    }

    @Override
    public void seedIfMissing(String email, long count, Duration window) {
        redisTemplate.opsForValue().setIfAbsent(key(email), Long.toString(count), window);
    }

    @Override
    public void replaceCount(String email, long count, Duration window) {
        redisTemplate.opsForValue().set(key(email), Long.toString(count), window);
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }
}
