package ar.edu.utn.frm.foodstore.core.error;

import ar.edu.utn.frm.foodstore.auth.PasswordExecutorSaturatedException;
import ar.edu.utn.frm.foodstore.auth.EmailAlreadyExistsException;
import ar.edu.utn.frm.foodstore.auth.InvalidCredentialsException;
import ar.edu.utn.frm.foodstore.auth.InvalidSessionException;
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
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio saturado",
                "El servicio está temporalmente saturado. Intentá nuevamente más tarde.",
                "SERVICIO_SATURADO");
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(EmailAlreadyExistsException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Email duplicado",
                "Ya existe una cuenta registrada con ese email.",
                "EMAIL_DUPLICADO");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Credenciales inválidas",
                "Email o contraseña incorrectos.",
                "CREDENCIALES_INVALIDAS");
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSession(InvalidSessionException exception) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Sesión inválida",
                "La cookie de sesión no es válida.",
                "SESION_INVALIDA");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setProperty("codigo", code);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
