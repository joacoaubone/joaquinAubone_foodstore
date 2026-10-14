package ar.edu.utn.frm.foodstore.core.error;

import ar.edu.utn.frm.foodstore.auth.PasswordExecutorSaturatedException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PasswordExecutorSaturatedException.class)
    public ResponseEntity<ProblemDetail> handlePasswordExecutorSaturation(
            PasswordExecutorSaturatedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio está temporalmente saturado. Intentá nuevamente más tarde.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Servicio saturado");
        problem.setProperty("codigo", "SERVICIO_SATURADO");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
