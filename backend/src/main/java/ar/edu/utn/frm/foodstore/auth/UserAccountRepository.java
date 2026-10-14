package ar.edu.utn.frm.foodstore.auth;

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
        return new UserIdentity(userId, email, java.util.List.of(roleCode));
    }
}
