package ar.edu.utn.frm.foodstore.core.ports;

import java.time.Duration;

public interface LoginAttemptRedisPort {

    Long currentCount(String email);

    long increment(String email, Duration window);

    void seedIfMissing(String email, long count, Duration window);

    void replaceCount(String email, long count, Duration window);
}
