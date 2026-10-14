package ar.edu.utn.frm.foodstore.auth;

public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("La sesión no es válida");
    }

    public InvalidSessionException(Throwable cause) {
        super("La sesión no es válida", cause);
    }
}
