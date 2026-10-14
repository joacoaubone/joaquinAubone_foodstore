package ar.edu.utn.frm.foodstore.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record SessionInfo(@JsonProperty("expira_en") OffsetDateTime expiresAt) {
}
