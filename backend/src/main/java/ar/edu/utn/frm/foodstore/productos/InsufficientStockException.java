package ar.edu.utn.frm.foodstore.productos;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(long productId) {
        super("El ajuste dejaría negativo el stock del producto " + productId);
    }
}
