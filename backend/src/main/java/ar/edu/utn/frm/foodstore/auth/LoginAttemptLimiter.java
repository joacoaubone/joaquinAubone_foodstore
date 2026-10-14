package ar.edu.utn.frm.foodstore.auth;

import ar.edu.utn.frm.foodstore.core.ports.LoginAttemptRedisPort;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptLimiter {

    private final LoginAttemptRepository attemptRepository;
    private final LoginAttemptRedisPort redisPort;
    private final int maximumAttempts;
    private final Duration window;
    private final AtomicBoolean redisWasUnavailable = new AtomicBoolean();

    @Autowired
    public LoginAttemptLimiter(
            LoginAttemptRepository attemptRepository,
            LoginAttemptRedisPort redisPort,
            @Value("${foodstore.login.max-intentos:5}") int maximumAttempts,
            @Value("${foodstore.login.ventana-min:15}") int windowMinutes) {
        this(attemptRepository, redisPort, maximumAttempts, Duration.ofMinutes(windowMinutes));
    }

    public LoginAttemptLimiter(
            LoginAttemptRepository attemptRepository,
            LoginAttemptRedisPort redisPort,
            int maximumAttempts,
            Duration window) {
        if (maximumAttempts < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("La política de intentos debe tener límites positivos");
        }
        this.attemptRepository = attemptRepository;
        this.redisPort = redisPort;
        this.maximumAttempts = maximumAttempts;
        this.window = window;
    }

    public boolean isLimitExceeded(String email) {
        try {
            if (redisWasUnavailable.getAndSet(false)) {
                return reconcileAfterRedisRecovery(email);
            }

            Long redisCount = redisPort.currentCount(email);
            if (redisCount != null) {
                return redisCount >= maximumAttempts;
            }

            long durableCount = attemptRepository.countRecentFailures(email, (int) window.toMinutes());
            if (durableCount > 0) {
                redisPort.seedIfMissing(email, durableCount, window);
            }
            return durableCount >= maximumAttempts;
        } catch (DataAccessException exception) {
            redisWasUnavailable.set(true);
            return attemptRepository.countRecentFailures(email, (int) window.toMinutes()) >= maximumAttempts;
        }
    }

    public void recordAttempt(String email, boolean successful) {
        attemptRepository.record(email, successful);
        if (successful) {
            return;
        }

        try {
            redisPort.increment(email, window);
        } catch (DataAccessException exception) {
            redisWasUnavailable.set(true);
        }
    }

    private boolean reconcileAfterRedisRecovery(String email) {
        long durableCount = attemptRepository.countRecentFailures(email, (int) window.toMinutes());
        Long redisCount = redisPort.currentCount(email);
        long count = Math.max(durableCount, redisCount == null ? 0L : redisCount);
        if (count > 0) {
            redisPort.replaceCount(email, count, window);
        }
        return count >= maximumAttempts;
    }
}
