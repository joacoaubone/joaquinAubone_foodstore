package ar.edu.utn.frm.foodstore.productos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductRead(
        long id,
        String nombre,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal precio,
        @JsonProperty("stock_cantidad") int stockCantidad,
        boolean disponible,
        @JsonProperty("creado_en") OffsetDateTime creadoEn) {
}
