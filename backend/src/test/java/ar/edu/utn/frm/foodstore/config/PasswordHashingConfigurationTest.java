package ar.edu.utn.frm.foodstore.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class PasswordHashingConfigurationTest {

    private final PasswordHashingConfiguration configuration = new PasswordHashingConfiguration();

    @Test
    void createsTwoPlatformWorkersAndQueueForEightJobsByDefault() {
        ExecutorService executor = configuration.bcryptExecutor(2);

        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
            assertThat(pool.getCorePoolSize()).isEqualTo(2);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
            assertThat(pool.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(8);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsNonPositiveConcurrency() {
        assertThatThrownBy(() -> configuration.bcryptExecutor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debe ser positivo");
    }
}
