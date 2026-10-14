package ar.edu.utn.frm.foodstore.config;

import ar.edu.utn.frm.foodstore.auth.PasswordHashingService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordHashingConfiguration {

    @Bean(name = "bcryptExecutor", destroyMethod = "shutdown")
    ExecutorService bcryptExecutor(
            @Value("${foodstore.bcrypt.max-concurrente:2}") int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("foodstore.bcrypt.max-concurrente debe ser positivo");
        }

        return new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.multiplyExact(maxConcurrent, 4)),
                Thread.ofPlatform().name("foodstore-bcrypt-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    PasswordHashingService passwordHashingService(
            PasswordEncoder passwordEncoder,
            @Qualifier("bcryptExecutor") ExecutorService bcryptExecutor) {
        return new PasswordHashingService(passwordEncoder, bcryptExecutor);
    }
}
