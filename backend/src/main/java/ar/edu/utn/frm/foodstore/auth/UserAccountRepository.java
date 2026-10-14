package ar.edu.utn.frm.foodstore.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean emailExists(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario WHERE email = ?)",
                Boolean.class,
                email);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<StoredUserAccount> findCredentialsByEmail(String email) {
        List<StoredUserAccount> rows = jdbcTemplate.query("""
                SELECT u.id, u.email, u.password_hash, ur.rol_codigo
                FROM usuario u
                JOIN usuario_rol ur ON ur.usuario_id = u.id
                WHERE u.email = ?
                ORDER BY ur.rol_codigo
                """, (resultSet, rowNumber) -> new StoredUserAccount(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash").trim(),
                List.of(resultSet.getString("rol_codigo"))), email);

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        StoredUserAccount first = rows.getFirst();
        List<String> roles = rows.stream()
                .flatMap(account -> account.roles().stream())
                .distinct()
                .toList();
        return Optional.of(new StoredUserAccount(first.id(), first.email(), first.passwordHash(), roles));
    }

    public UserIdentity create(String email, String passwordHash, String roleCode) {
        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO usuario (email, password_hash)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, email, passwordHash);
        jdbcTemplate.update(
                "INSERT INTO usuario_rol (usuario_id, rol_codigo) VALUES (?, ?)",
                userId,
                roleCode);
        return new UserIdentity(userId, email, List.of(roleCode));
    }
}
