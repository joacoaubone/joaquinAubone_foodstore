package ar.edu.utn.frm.foodstore.productos;

public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException(int page, int size) {
        super("page debe ser mayor o igual a cero y size debe ser positivo");
    }
}
