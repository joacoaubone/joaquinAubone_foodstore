package ar.edu.utn.frm.foodstore.productos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductUpdateRequest(
        @JsonProperty("nombre") @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*") String nombre,
        @JsonProperty("precio") @DecimalMin(value = "0.00", inclusive = false) BigDecimal precio,
        @JsonProperty("disponible") Boolean disponible) {
}
