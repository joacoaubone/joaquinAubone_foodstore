package ar.edu.utn.frm.foodstore.productos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record StockMovementRead(
        long id,
        @JsonProperty("producto_id") long productoId,
        String tipo,
        int cantidad,
        @JsonProperty("pedido_id") Long pedidoId,
        @JsonProperty("creado_en") OffsetDateTime creadoEn) {
}
