package ar.edu.utn.frm.foodstore.productos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockMovementRepository {

    private final JdbcTemplate jdbcTemplate;

    public StockMovementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LockedProduct> lockProduct(long productId) {
        return jdbcTemplate.query("""
                SELECT id, stock_cantidad
                FROM producto
                WHERE id = ?
                FOR UPDATE
                """, (resultSet, rowNumber) -> new LockedProduct(
                resultSet.getLong("id"),
                resultSet.getInt("stock_cantidad")), productId)
                .stream()
                .findFirst();
    }

    public StockMovementRead insertMovement(long productId, String type, int quantity) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO movimiento_stock (producto_id, tipo, cantidad)
                VALUES (?, ?, ?)
                RETURNING id, producto_id, tipo, cantidad, pedido_id, creado_en
                """, (resultSet, rowNumber) -> mapMovement(resultSet), productId, type, quantity);
    }

    public void updateStock(long productId, int newStock) {
        int updated = jdbcTemplate.update(
                "UPDATE producto SET stock_cantidad = ? WHERE id = ?",
                newStock,
                productId);
        if (updated != 1) {
            throw new ProductNotFoundException(productId);
        }
    }

    public PageResponse<StockMovementRead> findPage(Long productId, int page, int size) {
        long offset = (long) page * size;
        List<StockMovementRead> items;
        Long total;
        if (productId == null) {
            items = jdbcTemplate.query("""
                    SELECT id, producto_id, tipo, cantidad, pedido_id, creado_en
                    FROM movimiento_stock
                    ORDER BY creado_en DESC, id DESC
                    LIMIT ? OFFSET ?
                    """, (resultSet, rowNumber) -> mapMovement(resultSet), size, offset);
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM movimiento_stock", Long.class);
        } else {
            items = jdbcTemplate.query("""
                    SELECT id, producto_id, tipo, cantidad, pedido_id, creado_en
                    FROM movimiento_stock
                    WHERE producto_id = ?
                    ORDER BY creado_en DESC, id DESC
                    LIMIT ? OFFSET ?
                    """, (resultSet, rowNumber) -> mapMovement(resultSet), productId, size, offset);
            total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM movimiento_stock WHERE producto_id = ?", Long.class, productId);
        }
        return new PageResponse<>(items, total == null ? 0L : total, page, size);
    }

    private static StockMovementRead mapMovement(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new StockMovementRead(
                resultSet.getLong("id"),
                resultSet.getLong("producto_id"),
                resultSet.getString("tipo"),
                resultSet.getInt("cantidad"),
                (Long) resultSet.getObject("pedido_id"),
                resultSet.getObject("creado_en", OffsetDateTime.class));
    }

    public record LockedProduct(long id, int stockQuantity) {
    }
}
