package ar.edu.utn.frm.foodstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@Testcontainers
class FoodstoreBackendApplicationTests {

    private static final String TEST_ADMIN_HASH = "$2a$12$" + "a".repeat(53);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("foodstore")
            .withUsername("foodstore")
            .withPassword("test-password");

    @Autowired
    JdbcTemplate jdbcTemplate;

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
}
