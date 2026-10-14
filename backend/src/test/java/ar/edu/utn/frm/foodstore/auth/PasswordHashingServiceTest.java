package ar.edu.utn.frm.foodstore.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingServiceTest {

    @Test
    void hashesAndVerifiesPasswordWithStrengthTwelveOnPlatformExecutorThread() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4),
                Thread.ofPlatform().name("test-bcrypt-", 0).factory());
        AtomicReference<Thread> hashingThread = new AtomicReference<>();
        PasswordEncoder encoder = new BCryptPasswordEncoder(12) {
            @Override
            public String encode(CharSequence rawPassword) {
                hashingThread.set(Thread.currentThread());
                return super.encode(rawPassword);
            }
        };
        PasswordHashingService service = new PasswordHashingService(encoder, executor);

        try {
            String hash = service.hash("foodstore-test-password");

            assertThat(hash).startsWith("$2a$12$").hasSize(60);
            assertThat(hashingThread.get().getName()).startsWith("test-bcrypt-");
            assertThat(hashingThread.get().isVirtual()).isFalse();
            assertThat(service.matches("foodstore-test-password", hash)).isTrue();
            assertThat(service.matches("incorrect-password", hash)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void boundedExecutorRejectsWhenWorkersAndQueueAreFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordEncoder blockingEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                started.countDown();
                awaitRelease(release);
                return "encoded";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                started.countDown();
                awaitRelease(release);
                return true;
            }
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().name("test-bcrypt-saturated-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        PasswordHashingService service = new PasswordHashingService(blockingEncoder, executor);
        ExecutorService requestThreads = Executors.newFixedThreadPool(2);

        try {
            var running = requestThreads.submit(() -> service.hash("first"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var queued = requestThreads.submit(() -> service.hash("second"));

            long queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (executor.getQueue().size() != 1 && System.nanoTime() < queueDeadline) {
                Thread.onSpinWait();
            }
            assertThat(executor.getQueue()).hasSize(1);
            assertThatThrownBy(() -> service.hash("third"))
                    .isInstanceOf(PasswordExecutorSaturatedException.class);
        } finally {
            release.countDown();
            requestThreads.shutdownNow();
            executor.shutdownNow();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test executor was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
