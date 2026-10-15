package ar.edu.utn.frm.foodstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ar.edu.utn.frm.foodstore.auth.PasswordHashingService;
import ar.edu.utn.frm.foodstore.auth.AuthenticationController;
import ar.edu.utn.frm.foodstore.auth.AuthenticationService;
import ar.edu.utn.frm.foodstore.auth.JwtTokenService;
import ar.edu.utn.frm.foodstore.auth.LoginAttemptLimiter;
import ar.edu.utn.frm.foodstore.auth.LoginAttemptRepository;
import ar.edu.utn.frm.foodstore.auth.TooManyLoginAttemptsException;
import ar.edu.utn.frm.foodstore.auth.UserAccountRepository;
import ar.edu.utn.frm.foodstore.auth.SessionCookieFactory;
import ar.edu.utn.frm.foodstore.core.ports.LoginAttemptRedisPort;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordHashingService passwordHashingService;

    @Autowired
    LoginAttemptRepository loginAttemptRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    SessionCookieFactory sessionCookieFactory;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", postgres::getJdbcUrl);
        registry.add("DATABASE_USERNAME", postgres::getUsername);
        registry.add("DATABASE_PASSWORD", postgres::getPassword);
        registry.add("ADMIN_PASSWORD_HASH", () -> TEST_ADMIN_HASH);
        registry.add("REDIS_URL", () -> "redis://%s:%d/0".formatted(redis.getHost(), redis.getMappedPort(6379)));
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
    void rolesCatalogIsPublicOrderedAndContainsNoAccountData() throws Exception {
        var response = mockMvc.perform(get("/api/v1/roles")
                        .cookie(new Cookie("access_token", "malformed-session-cookie")))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .isEqualTo("[\"ADMIN\",\"CLIENTE\",\"PEDIDOS\",\"STOCK\"]");
        assertThat(response.getContentAsString()).doesNotContain("email", "password", "usuario");
    }

    @Test
    void productCreationUsesStockLedgerAndOnlyAdminCanCreateOrEditProducts() throws Exception {
        String adminToken = adminAccessToken();
        String productName = "product-" + UUID.randomUUID();
        var created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/productos")
                        .cookie(new Cookie("access_token", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","precio":12.50,"stock_cantidad":9,"disponible":true}
                                """.formatted(productName)))
                .andReturn()
                .getResponse();

        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getContentAsString()).contains("\"precio\":\"12.50\"", "\"stock_cantidad\":9");
        Long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM producto WHERE nombre = ?", Long.class, productName);
        Long movementCountAfterCreate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_stock WHERE producto_id = ?", Long.class, productId);

        var updated = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/productos/{id}", productId)
                        .cookie(new Cookie("access_token", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s-updated","precio":15.00,"disponible":false,"stock_cantidad":99}
                                """.formatted(productName)))
                .andReturn()
                .getResponse();
        Long stockAfterUpdate = jdbcTemplate.queryForObject(
                "SELECT stock_cantidad FROM producto WHERE id = ?", Long.class, productId);
        Long movementCountAfterUpdate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_stock WHERE producto_id = ?", Long.class, productId);

        assertThat(updated.getStatus()).isEqualTo(200);
        assertThat(updated.getContentAsString()).contains("\"precio\":\"15.00\"", "\"disponible\":false");
        assertThat(stockAfterUpdate).isEqualTo(9L);
        assertThat(movementCountAfterUpdate).isEqualTo(movementCountAfterCreate);

        String stockEmail = "stock-product-" + UUID.randomUUID() + "@example.test";
        var stockUser = createUser(stockEmail, "stock-password", "STOCK", adminToken).getResponse();
        assertThat(stockUser.getStatus()).isEqualTo(201);
        String stockToken = cookieValue(
                login(stockEmail, "stock-password").getResponse().getHeaders("Set-Cookie"), "access_token");
        String forbiddenProductName = "forbidden-" + UUID.randomUUID();
        var forbiddenCreate = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/productos")
                        .cookie(new Cookie("access_token", stockToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","precio":1.00,"stock_cantidad":0,"disponible":true}
                                """.formatted(forbiddenProductName)))
                .andReturn()
                .getResponse();
        var forbiddenUpdate = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/productos/{id}", productId)
                        .cookie(new Cookie("access_token", stockToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precio\":20.00}"))
                .andReturn()
                .getResponse();

        assertThat(forbiddenCreate.getStatus()).isEqualTo(403);
        assertThat(forbiddenUpdate.getStatus()).isEqualTo(403);
        assertThat(forbiddenCreate.getContentAsString()).contains("ROL_INSUFICIENTE");
        Long forbiddenProductCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM producto WHERE nombre = ?", Long.class, forbiddenProductName);
        assertThat(forbiddenProductCount).isZero();
    }

    @Test
    void productValidationRejectsInvalidNameAndPrice() throws Exception {
        String adminToken = adminAccessToken();
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/productos")
                        .cookie(new Cookie("access_token", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":" ","precio":0,"stock_cantidad":0,"disponible":true}
                                """))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("CUERPO_INVALIDO", "nombre", "precio");
    }

    @Test
    void productCatalogIsPublicPaginatedAndUsesTheAgreedEnvelope() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE movimiento_stock, producto RESTART IDENTITY CASCADE");
        String suffix = UUID.randomUUID().toString();
        insertTestProduct("page-a-" + suffix, "1.25", 3, true);
        insertTestProduct("page-b-" + suffix, "2.50", 7, false);
        insertTestProduct("page-c-" + suffix, "3.75", 0, true);

        var firstPage = mockMvc.perform(get("/api/v1/productos")
                        .param("page", "0")
                        .param("size", "2")
                        .cookie(new Cookie("access_token", "malformed-token")))
                .andReturn()
                .getResponse();
        var secondPage = mockMvc.perform(get("/api/v1/productos")
                        .param("page", "1")
                        .param("size", "2"))
                .andReturn()
                .getResponse();

        assertThat(firstPage.getStatus()).isEqualTo(200);
        assertThat(firstPage.getContentAsString())
                .contains("\"total\":3", "\"page\":0", "\"size\":2", "\"precio\":\"1.25\"");
        assertThat(firstPage.getContentAsString()).contains("\"items\":[");
        assertThat(secondPage.getStatus()).isEqualTo(200);
        assertThat(secondPage.getContentAsString())
                .contains("\"total\":3", "\"page\":1", "\"size\":2", "page-c-" + suffix);
    }

    private void insertTestProduct(String name, String price, int stock, boolean available) {
        jdbcTemplate.update("""
                INSERT INTO producto (nombre, precio, stock_cantidad, disponible)
                VALUES (?, CAST(? AS NUMERIC(10,2)), ?, ?)
                """, name, price, stock, available);
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

    @Test
    void stockMovementEndpointsMaintainTheLedgerAndPaginatedReadModel() throws Exception {
        long productId = createProduct("movement-ledger-" + UUID.randomUUID());
        String adminToken = adminAccessToken();

        var entry = postStockMovement(productId, "ENTRADA", 10, adminToken).getResponse();
        var adjustment = postStockMovement(productId, "AJUSTE_MANUAL", -3, adminToken).getResponse();
        var firstPage = mockMvc.perform(get("/api/v1/movimientos-stock")
                        .param("page", "0")
                        .param("size", "1")
                        .param("producto_id", Long.toString(productId))
                        .cookie(new Cookie("access_token", adminToken)))
                .andReturn()
                .getResponse();
        Long stockQuantity = jdbcTemplate.queryForObject(
                "SELECT stock_cantidad FROM producto WHERE id = ?", Long.class, productId);
        Long ledgerTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cantidad), 0) FROM movimiento_stock WHERE producto_id = ?",
                Long.class,
                productId);

        assertThat(entry.getStatus()).isEqualTo(201);
        assertThat(adjustment.getStatus()).isEqualTo(201);
        assertThat(stockQuantity).isEqualTo(7L);
        assertThat(ledgerTotal).isEqualTo(stockQuantity);
        assertThat(firstPage.getStatus()).isEqualTo(200);
        assertThat(firstPage.getContentAsString())
                .contains("\"total\":2", "\"page\":0", "\"size\":1", "producto_id");
        assertThat(firstPage.getContentAsString()).contains("\"items\":[");
    }

    @Test
    void invalidAndInsufficientStockMovementsLeaveProductAndLedgerUnchanged() throws Exception {
        long productId = createProduct("movement-rollback-" + UUID.randomUUID());
        String adminToken = adminAccessToken();
        postStockMovement(productId, "ENTRADA", 5, adminToken);

        var insufficient = postStockMovement(productId, "AJUSTE_MANUAL", -6, adminToken).getResponse();
        var zeroAdjustment = postStockMovement(productId, "AJUSTE_MANUAL", 0, adminToken).getResponse();
        Long stockQuantity = jdbcTemplate.queryForObject(
                "SELECT stock_cantidad FROM producto WHERE id = ?", Long.class, productId);
        Long movementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movimiento_stock WHERE producto_id = ?", Long.class, productId);

        assertThat(insufficient.getStatus()).isEqualTo(422);
        assertThat(insufficient.getContentAsString()).contains("STOCK_INSUFICIENTE");
        assertThat(zeroAdjustment.getStatus()).isEqualTo(422);
        assertThat(zeroAdjustment.getContentAsString()).contains("CUERPO_INVALIDO");
        assertThat(stockQuantity).isEqualTo(5L);
        assertThat(movementCount).isEqualTo(1L);
    }

    @Test
    void stockMovementPermissionsAndRowLocksPreventUnauthorizedOrNegativeUpdates() throws Exception {
        long productId = createProduct("movement-lock-" + UUID.randomUUID());
        String adminToken = adminAccessToken();
        postStockMovement(productId, "ENTRADA", 10, adminToken);

        String stockEmail = "stock-movement-" + UUID.randomUUID() + "@example.test";
        var provisioned = createUser(stockEmail, "stock-password", "STOCK", adminToken).getResponse();
        assertThat(provisioned.getStatus()).isEqualTo(201);
        String stockToken = cookieValue(
                login(stockEmail, "stock-password").getResponse().getHeaders("Set-Cookie"), "access_token");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService concurrentRequests = Executors.newFixedThreadPool(2);

        try {
            var adjustment = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent stock test did not start");
                }
                return postStockMovement(productId, "AJUSTE_MANUAL", -7, stockToken)
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = concurrentRequests.submit(adjustment);
            Future<Integer> second = concurrentRequests.submit(adjustment);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        } finally {
            start.countDown();
            concurrentRequests.shutdownNow();
        }

        String clientEmail = "stock-denied-" + UUID.randomUUID() + "@example.test";
        register(clientEmail, "client-password");
        String clientToken = cookieValue(
                login(clientEmail, "client-password").getResponse().getHeaders("Set-Cookie"), "access_token");
        var denied = postStockMovement(productId, "AJUSTE_MANUAL", -1, clientToken).getResponse();
        Long stockQuantity = jdbcTemplate.queryForObject(
                "SELECT stock_cantidad FROM producto WHERE id = ?", Long.class, productId);
        Long ledgerTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cantidad), 0) FROM movimiento_stock WHERE producto_id = ?",
                Long.class,
                productId);

        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(denied.getContentAsString()).contains("ROL_INSUFICIENTE");
        assertThat(stockQuantity).isEqualTo(3L);
        assertThat(ledgerTotal).isEqualTo(stockQuantity);
    }

    @Test
    void loginRateLimitReturns429AfterFiveFailedAttemptsAndPersistsEveryAttempt() throws Exception {
        String email = "rate-limit-" + UUID.randomUUID() + "@example.test";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(email, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        var blocked = login(email, "wrong-password").getResponse();

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(blocked.getContentAsString()).contains("LIMITE_INTENTOS_SUPERADO");
        Long recordedAttempts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM intento_acceso WHERE email = ? AND exitoso = FALSE
                """, Long.class, email);
        assertThat(recordedAttempts).isEqualTo(6);
    }

    @Test
    void loginRateLimitFallsBackToPostgresAndReconcilesAfterRedisRecovery() throws Exception {
        String email = "rate-fallback-" + UUID.randomUUID() + "@example.test";
        SwitchableRedisCounter redisCounter = new SwitchableRedisCounter(false);
        LoginAttemptLimiter fallbackLimiter = new LoginAttemptLimiter(
                loginAttemptRepository, redisCounter, 5, Duration.ofMinutes(15));
        AuthenticationService fallbackAuthentication = new AuthenticationService(
                userAccountRepository, passwordHashingService, jwtTokenService, fallbackLimiter);
        MockMvc fallbackMockMvc = MockMvcBuilders
                .standaloneSetup(new AuthenticationController(fallbackAuthentication, sessionCookieFactory))
                .setControllerAdvice(new ar.edu.utn.frm.foodstore.core.error.ApiExceptionHandler())
                .build();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(fallbackMockMvc, email, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        var blockedWhileRedisIsUnavailable = login(fallbackMockMvc, email, "wrong-password").getResponse();
        assertThat(blockedWhileRedisIsUnavailable.getStatus()).isEqualTo(429);
        assertThat(blockedWhileRedisIsUnavailable.getContentAsString()).contains("LIMITE_INTENTOS_SUPERADO");

        redisCounter.setAvailable(true);
        var blockedAfterRedisRecovery = login(fallbackMockMvc, email, "wrong-password").getResponse();

        assertThat(blockedAfterRedisRecovery.getStatus()).isEqualTo(429);
        assertThat(redisCounter.currentCount(email)).isEqualTo(7L);
        Long recordedAttempts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM intento_acceso WHERE email = ? AND exitoso = FALSE
                """, Long.class, email);
        assertThat(recordedAttempts).isEqualTo(7);
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
        return login(mockMvc, email, password);
    }

    private org.springframework.test.web.servlet.MvcResult login(MockMvc target, String email, String password)
            throws Exception {
        return target.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
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

    private long createProduct(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO producto (nombre, precio, stock_cantidad, disponible)
                VALUES (?, 12.50, 0, TRUE)
                RETURNING id
                """, Long.class, name);
    }

    private String adminAccessToken() throws Exception {
        String password = "movement-admin-" + UUID.randomUUID();
        jdbcTemplate.update(
                "UPDATE usuario SET password_hash = ? WHERE email = 'admin@example.test'",
                passwordHashingService.hash(password));
        return cookieValue(
                login("admin@example.test", password).getResponse().getHeaders("Set-Cookie"), "access_token");
    }

    private org.springframework.test.web.servlet.MvcResult postStockMovement(
            long productId, String type, int quantity, String accessToken) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/movimientos-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("access_token", accessToken))
                        .content("""
                                {"producto_id":%d,"tipo":"%s","cantidad":%d}
                                """.formatted(productId, type, quantity)))
                .andReturn();
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

    private static final class SwitchableRedisCounter implements LoginAttemptRedisPort {

        private final Map<String, Long> counts = new ConcurrentHashMap<>();
        private volatile boolean available;

        private SwitchableRedisCounter(boolean available) {
            this.available = available;
        }

        @Override
        public Long currentCount(String email) {
            requireAvailable();
            return counts.get(email);
        }

        @Override
        public long increment(String email, Duration window) {
            requireAvailable();
            return counts.merge(email, 1L, Long::sum);
        }

        @Override
        public void seedIfMissing(String email, long count, Duration window) {
            requireAvailable();
            counts.putIfAbsent(email, count);
        }

        @Override
        public void replaceCount(String email, long count, Duration window) {
            requireAvailable();
            counts.put(email, count);
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }

        private void requireAvailable() {
            if (!available) {
                throw new RedisConnectionFailureException("Redis de prueba no disponible");
            }
        }
    }
}
