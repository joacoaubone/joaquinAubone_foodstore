package ar.edu.utn.frm.foodstore.catalogos;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleCatalogRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoleCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findRoleCodes() {
        return jdbcTemplate.query(
                "SELECT codigo FROM rol ORDER BY codigo",
                (resultSet, rowNumber) -> resultSet.getString("codigo"));
    }
}
