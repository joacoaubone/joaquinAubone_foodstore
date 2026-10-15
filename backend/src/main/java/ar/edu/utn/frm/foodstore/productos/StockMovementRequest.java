package ar.edu.utn.frm.foodstore.productos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record StockMovementRequest(
        @JsonProperty("producto_id") @NotNull @Positive Long productoId,
        @JsonProperty("tipo") @NotNull @Pattern(regexp = "ENTRADA|AJUSTE_MANUAL") String tipo,
        @JsonProperty("cantidad") @NotNull Integer cantidad) {

    @JsonIgnore
    @AssertTrue(message = "La cantidad debe tener signo válido para el tipo de movimiento")
    public boolean hasValidSignForType() {
        if (tipo == null || cantidad == null) {
            return true;
        }
        return switch (tipo) {
            case "ENTRADA" -> cantidad > 0;
            case "AJUSTE_MANUAL" -> cantidad != 0;
            default -> false;
        };
    }
}
