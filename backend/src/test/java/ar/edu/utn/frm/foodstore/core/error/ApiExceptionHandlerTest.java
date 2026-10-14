package ar.edu.utn.frm.foodstore.core.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frm.foodstore.auth.PasswordHashingService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    @Test
    void mapsSaturatedPasswordExecutorToRfc9457HttpResponse() throws Exception {
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
                return false;
            }
        };
        ThreadPoolExecutor bcryptExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().name("test-bcrypt-http-", 0).factory());
        ExecutorService requests = Executors.newFixedThreadPool(2);
        PasswordHashingService hashingService = new PasswordHashingService(blockingEncoder, bcryptExecutor);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PasswordController(hashingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        try {
            Future<?> running = requests.submit(() -> hashingService.hash("first"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> queued = requests.submit(() -> hashingService.hash("second"));
            long queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (bcryptExecutor.getQueue().size() != 1 && System.nanoTime() < queueDeadline) {
                Thread.onSpinWait();
            }
            assertThat(bcryptExecutor.getQueue()).hasSize(1);

            mockMvc.perform(post("/password-hash"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.codigo").value("SERVICIO_SATURADO"));

            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            queued.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            requests.shutdownNow();
            bcryptExecutor.shutdownNow();
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

    @RestController
    private static class PasswordController {

        private final PasswordHashingService hashingService;

        private PasswordController(PasswordHashingService hashingService) {
            this.hashingService = hashingService;
        }

        @PostMapping("/password-hash")
        void hashPassword() {
            hashingService.hash("third");
        }
    }
}
