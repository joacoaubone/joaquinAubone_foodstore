package ar.edu.utn.frm.foodstore.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LoginAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoginAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String email, boolean successful) {
        jdbcTemplate.update(
                "INSERT INTO intento_acceso (email, exitoso) VALUES (?, ?)",
                email,
                successful);
    }

    public long countRecentFailures(String email, int windowMinutes) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM intento_acceso
                WHERE email = ?
                  AND exitoso = FALSE
                  AND creado_en >= NOW() - (? * INTERVAL '1 minute')
                """, Long.class, email, windowMinutes);
        return count == null ? 0L : count;
    }
}
