package ar.edu.utn.frm.foodstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@Testcontainers
@AutoConfigureMockMvc
class FoodstoreBackendApplicationTests {

    private static final String TEST_ADMIN_HASH = "$2a$12$" + "a".repeat(53);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("foodstore")
            .withUsername("foodstore")
            .withPassword("test-password");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", postgres::getJdbcUrl);
        registry.add("DATABASE_USERNAME", postgres::getUsername);
        registry.add("DATABASE_PASSWORD", postgres::getPassword);
        registry.add("ADMIN_PASSWORD_HASH", () -> TEST_ADMIN_HASH);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesTheFoodStoreSchemaAndSeedsReferenceData() {
        Long tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Long.class);
        Long successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Long.class);
        Long roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rol", Long.class);
        Long orderStateCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM estado_pedido", Long.class);
        Long paymentMethodCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM forma_pago", Long.class);
        Long adminCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM usuario u
                JOIN usuario_rol ur ON ur.usuario_id = u.id
                WHERE u.email = 'admin@example.test' AND ur.rol_codigo = 'ADMIN'
                """, Long.class);
        String adminHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM usuario WHERE email = 'admin@example.test'", String.class);
        Boolean cashEnabled = jdbcTemplate.queryForObject(
                "SELECT habilitada FROM forma_pago WHERE codigo = 'EFECTIVO'", Boolean.class);

        assertThat(tableCount).isEqualTo(14);
        assertThat(successfulMigrations).isEqualTo(2);
        assertThat(roleCount).isEqualTo(4);
        assertThat(orderStateCount).isEqualTo(5);
        assertThat(paymentMethodCount).isEqualTo(3);
        assertThat(adminCount).isEqualTo(1);
        assertThat(adminHash).startsWith("$2a$12$").hasSize(60);
        assertThat(cashEnabled).isFalse();
    }

    @Test
    void publicRegistrationCreatesCustomerWithoutReturningCredentials() throws Exception {
        String email = "client-" + UUID.randomUUID() + "@example.test";

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password-123"}
                                """.formatted(email)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains(email, "CLIENTE");
        assertThat(response.getContentAsString()).doesNotContain("password", "access_token", "refresh_token");
        Long customerRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM usuario u
                JOIN usuario_rol ur ON ur.usuario_id = u.id
                WHERE u.email = ? AND ur.rol_codigo = 'CLIENTE'
                """, Long.class, email);
        assertThat(customerRoleCount).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsForTheSameEmailCreateOnlyOneCustomer() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.test";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService requests = Executors.newFixedThreadPool(2);

        try {
            var registration = (java.util.concurrent.Callable<RegistrationOutcome>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent registration test did not start");
                }
                var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password-123"}
                                        """.formatted(email)))
                        .andReturn()
                        .getResponse();
                return new RegistrationOutcome(response.getStatus(), response.getContentAsString());
            };
            Future<RegistrationOutcome> first = requests.submit(registration);
            Future<RegistrationOutcome> second = requests.submit(registration);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RegistrationOutcome> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(RegistrationOutcome::status)
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(outcomes.stream()
                    .filter(outcome -> outcome.status() == 409)
                    .findFirst()
                    .orElseThrow()
                    .body()).contains("EMAIL_DUPLICADO");
            Long accountCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, email);
            assertThat(accountCount).isEqualTo(1);
        } finally {
            start.countDown();
            requests.shutdownNow();
        }
    }

    private record RegistrationOutcome(int status, String body) {
    }
}
