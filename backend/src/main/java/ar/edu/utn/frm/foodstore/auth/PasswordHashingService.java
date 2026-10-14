package ar.edu.utn.frm.foodstore.auth;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Callable;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordHashingService {

    private final PasswordEncoder passwordEncoder;
    private final ExecutorService bcryptExecutor;

    public PasswordHashingService(PasswordEncoder passwordEncoder, ExecutorService bcryptExecutor) {
        this.passwordEncoder = passwordEncoder;
        this.bcryptExecutor = bcryptExecutor;
    }

    public String hash(String rawPassword) {
        return execute(() -> passwordEncoder.encode(rawPassword));
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return execute(() -> passwordEncoder.matches(rawPassword, encodedPassword));
    }

    private <T> T execute(Callable<T> operation) {
        Future<T> result;
        try {
            result = bcryptExecutor.submit(operation);
        } catch (RejectedExecutionException exception) {
            throw new PasswordExecutorSaturatedException(exception);
        }

        try {
            return result.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió el cálculo de BCrypt", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Falló el cálculo de BCrypt", cause);
        }
    }
}
