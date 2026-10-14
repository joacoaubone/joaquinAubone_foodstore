package ar.edu.utn.frm.foodstore.auth;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Ya existe una cuenta registrada con ese email");
    }
}
