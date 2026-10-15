package ar.edu.utn.frm.foodstore.productos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(String name, BigDecimal price, boolean available) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO producto (nombre, precio, stock_cantidad, disponible)
                VALUES (?, ?, 0, ?)
                RETURNING id
                """, Long.class, name, price, available);
    }

    public Optional<ProductRead> findById(long id) {
        List<ProductRead> products = jdbcTemplate.query("""
                SELECT id, nombre, precio, stock_cantidad, disponible, creado_en
                FROM producto
                WHERE id = ?
                """, ProductRepository::mapProduct, id);
        return products.stream().findFirst();
    }

    public PageResponse<ProductRead> findPage(int page, int size) {
        long offset = (long) page * size;
        List<ProductRead> items = jdbcTemplate.query("""
                SELECT id, nombre, precio, stock_cantidad, disponible, creado_en
                FROM producto
                ORDER BY id
                LIMIT ? OFFSET ?
                """, ProductRepository::mapProduct, size, offset);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM producto", Long.class);
        return new PageResponse<>(items, total == null ? 0L : total, page, size);
    }

    public void update(long id, String name, BigDecimal price, boolean available) {
        int updated = jdbcTemplate.update("""
                UPDATE producto
                SET nombre = ?, precio = ?, disponible = ?
                WHERE id = ?
                """, name, price, available, id);
        if (updated != 1) {
            throw new ProductNotFoundException(id);
        }
    }

    private static ProductRead mapProduct(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ProductRead(
                resultSet.getLong("id"),
                resultSet.getString("nombre"),
                resultSet.getBigDecimal("precio"),
                resultSet.getInt("stock_cantidad"),
                resultSet.getBoolean("disponible"),
                resultSet.getObject("creado_en", OffsetDateTime.class));
    }
}
