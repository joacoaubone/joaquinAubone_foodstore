package ar.edu.utn.frm.foodstore.auth;

public class PasswordExecutorSaturatedException extends RuntimeException {

    public PasswordExecutorSaturatedException(Throwable cause) {
        super("El servicio de autenticación está temporalmente saturado", cause);
    }
}
