package ar.edu.utn.frm.foodstore.productos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductCreateRequest(
        @JsonProperty("nombre") @NotBlank @Size(max = 120) String nombre,
        @JsonProperty("precio") @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal precio,
        @JsonProperty("stock_cantidad") @PositiveOrZero int stockCantidad,
        @JsonProperty("disponible") Boolean disponible) {
}
