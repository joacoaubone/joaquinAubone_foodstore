package ar.edu.utn.frm.foodstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ar.edu.utn.frm.foodstore.auth.PasswordHashingService;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

    @Autowired
    PasswordHashingService passwordHashingService;

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

    @Test
    void loginSetsSecureHttpOnlySessionCookiesAndReturnsOnlyAccessExpiry() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password-123"}
                                """.formatted(email)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("expira_en");
        assertThat(response.getContentAsString())
                .doesNotContain("access_token", "refresh_token", "accessToken", "refreshToken");
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("access_token=")
                .contains("Path=/api/v1", "Max-Age=1800", "HttpOnly", "Secure", "SameSite=Lax"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/v1/auth/refresh", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Lax"));
    }

    @Test
    void invalidLoginDoesNotRevealWhetherEmailExists() throws Exception {
        String email = "invalid-login-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");

        var wrongPassword = login(email, "wrong-password").getResponse();
        var unknownEmail = login("unknown-" + UUID.randomUUID() + "@example.test", "wrong-password").getResponse();

        assertThat(wrongPassword.getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownEmail.getContentAsString());
        assertThat(wrongPassword.getContentAsString()).contains("CREDENCIALES_INVALIDAS");
    }

    @Test
    void logoutExpiresBothSessionCookiesWithMatchingSecurityAttributes() throws Exception {
        String email = "logout-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");
        var loginResponse = login(email, "password-123").getResponse();
        String accessToken = cookieValue(loginResponse.getHeaders("Set-Cookie"), "access_token");

        var logoutResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/logout")
                        .cookie(new Cookie("access_token", accessToken)))
                .andReturn()
                .getResponse();

        assertThat(logoutResponse.getStatus()).isEqualTo(204);
        List<String> cookies = logoutResponse.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("access_token=")
                .contains("Path=/api/v1", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax"));
        assertThat(cookies).anySatisfy(cookie -> assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/v1/auth/refresh", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax"));
    }

    @Test
    void refreshUsesOnlyRefreshTokenAndDoesNotRotateIt() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");
        var loginResponse = login(email, "password-123").getResponse();
        String refreshToken = cookieValue(loginResponse.getHeaders("Set-Cookie"), "refresh_token");

        var refreshResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andReturn()
                .getResponse();

        assertThat(refreshResponse.getStatus()).isEqualTo(200);
        assertThat(refreshResponse.getContentAsString()).contains("expira_en");
        List<String> cookies = refreshResponse.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst()).startsWith("access_token=").contains("Max-Age=1800", "HttpOnly", "Secure");
    }

    @Test
    void invalidOrWrongTypeTokensReturnRfc9457SessionError() throws Exception {
        String email = "token-type-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");
        var loginResponse = login(email, "password-123").getResponse();
        String accessToken = cookieValue(loginResponse.getHeaders("Set-Cookie"), "access_token");
        String refreshToken = cookieValue(loginResponse.getHeaders("Set-Cookie"), "refresh_token");

        var refreshWithAccess = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", accessToken)))
                .andReturn()
                .getResponse();
        var protectedWithRefresh = mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("access_token", refreshToken)))
                .andReturn()
                .getResponse();
        var protectedWithoutToken = mockMvc.perform(get("/api/v1/auth/me"))
                .andReturn()
                .getResponse();

        for (var response : List.of(refreshWithAccess, protectedWithRefresh, protectedWithoutToken)) {
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(response.getContentAsString()).contains("SESION_INVALIDA");
        }
    }

    @Test
    void currentIdentityComesFromTheAuthenticatedAccessToken() throws Exception {
        String email = "identity-" + UUID.randomUUID() + "@example.test";
        register(email, "password-123");
        String accessToken = cookieValue(login(email, "password-123").getResponse().getHeaders("Set-Cookie"),
                "access_token");

        var response = mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("access_token", accessToken)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains(email, "CLIENTE");
        assertThat(response.getContentAsString()).doesNotContain("password", "password_hash", "token");
    }

    @Test
    void accountProvisioningRequiresAdminAndAcceptsOnlySupportedRoles() throws Exception {
        String clientEmail = "provision-client-" + UUID.randomUUID() + "@example.test";
        register(clientEmail, "password-123");
        String clientAccessToken = cookieValue(
                login(clientEmail, "password-123").getResponse().getHeaders("Set-Cookie"), "access_token");
        String proposedUserEmail = "stock-" + UUID.randomUUID() + "@example.test";

        var anonymousResponse = createUser(proposedUserEmail, "stock-password", "STOCK", null).getResponse();
        var clientResponse = createUser(
                proposedUserEmail, "stock-password", "STOCK", clientAccessToken).getResponse();

        assertThat(anonymousResponse.getStatus()).isEqualTo(401);
        assertThat(clientResponse.getStatus()).isEqualTo(403);
        assertThat(clientResponse.getContentAsString()).contains("ROL_INSUFICIENTE");
        Long unauthorizedAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE email = ?", Long.class, proposedUserEmail);
        assertThat(unauthorizedAccounts).isZero();

        String adminPassword = "admin-login-" + UUID.randomUUID();
        jdbcTemplate.update(
                "UPDATE usuario SET password_hash = ? WHERE email = 'admin@example.test'",
                passwordHashingService.hash(adminPassword));
        String adminAccessToken = cookieValue(
                login("admin@example.test", adminPassword).getResponse().getHeaders("Set-Cookie"), "access_token");
        var adminResponse = createUser(proposedUserEmail, "stock-password", "STOCK", adminAccessToken)
                .getResponse();

        assertThat(adminResponse.getStatus()).isEqualTo(201);
        assertThat(adminResponse.getContentAsString()).contains("STOCK");
        assertThat(adminResponse.getContentAsString()).doesNotContain("password", "password_hash", "token");
    }

    @Test
    void publicRegistrationCannotChooseAnElevatedRole() throws Exception {
        String email = "role-injection-" + UUID.randomUUID() + "@example.test";

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password-123","rol_codigo":"ADMIN"}
                                """.formatted(email)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        Long adminRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM usuario u
                JOIN usuario_rol ur ON ur.usuario_id = u.id
                WHERE u.email = ? AND ur.rol_codigo = 'ADMIN'
                """, Long.class, email);
        Long customerRoleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM usuario u
                JOIN usuario_rol ur ON ur.usuario_id = u.id
                WHERE u.email = ? AND ur.rol_codigo = 'CLIENTE'
                """, Long.class, email);
        assertThat(adminRoleCount).isZero();
        assertThat(customerRoleCount).isEqualTo(1);
    }

    @Test
    void invalidRequestBodyUsesTheAdoptedProblemCodeAndFieldList() throws Exception {
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short"}
                                """))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("CUERPO_INVALIDO", "email", "password");
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn();
    }

    private org.springframework.test.web.servlet.MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn();
    }

    private org.springframework.test.web.servlet.MvcResult createUser(
            String email, String password, String role, String accessToken) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","rol_codigo":"%s"}
                        """.formatted(email, password, role));
        if (accessToken != null) {
            request.cookie(new Cookie("access_token", accessToken));
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String cookieValue(List<String> cookies, String name) {
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }

    private record RegistrationOutcome(int status, String body) {
    }
}
