package ar.edu.utn.frm.foodstore.auth;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Se superó el límite de intentos de login");
    }
}
